/*
 * This file is part of the Alitheia system, developed by the SQO-OSS
 * consortium as part of the IST FP6 SQO-OSS project, number 033331.
 *
 * Copyright 2007 - 2010 - Organization for Free and Open Source Software,  
 *                Athens, Greece.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package eu.sqooss.impl.service.scheduler;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.*;

import org.osgi.framework.BundleContext;

import eu.sqooss.service.logging.Logger;
import eu.sqooss.service.scheduler.Job;
import eu.sqooss.service.scheduler.ResumePoint;
import eu.sqooss.service.scheduler.Scheduler;
import eu.sqooss.service.scheduler.SchedulerException;
import eu.sqooss.service.scheduler.SchedulerStats;
import eu.sqooss.service.scheduler.WorkerThread;

public class SchedulerServiceImpl implements Scheduler {

    private static final String START_THREADS_PROPERTY = "eu.sqooss.scheduler.numthreads";
    private static final String PERF_LOG_PROPERTY = "eu.sqooss.log.perf";
    
    private Logger logger = null;
    private boolean perfLog = false;

    private SchedulerStats stats = new SchedulerStats();

    // thread safe job queue
    private PriorityQueue<Job> blockedQueue = new PriorityQueue<Job>(1,
            new JobPriorityComparator());

    private BlockingQueue<Job> failedQueue = new ArrayBlockingQueue<Job>(1000);

	private ExecutorService executorService;
	
	private List<Runnable> frozenJobs = new LinkedList<Runnable>();

    private List<WorkerThread> myWorkerThreads = null;
    
    private boolean isExecuting = false;
    
    public SchedulerServiceImpl() { }

    public void enqueue(Job job) throws SchedulerException {
        synchronized (this) {
            if (logger != null)
                logger.debug("SchedulerServiceImpl: queuing job " + job.toString());
            job.callAboutToBeEnqueued(this);
            blockedQueue.add(job);
            stats.addWaitingJob(job.getClass().toString());
            stats.incTotalJobs();
        }
        jobDependenciesChanged(job);
    }
    
    public void enqueueNoDependencies(Set<Job> jobs) throws SchedulerException {
        synchronized (this) {
            for (Job job : jobs) {
                if (logger != null)
                    logger.debug("Scheduler ServiceImpl: queuing job "
                        + job.toString());
                job.callAboutToBeEnqueued(this);

                Future<Void> future = executorService.submit(job); 
                job.future = future;

                stats.addWaitingJob(job.getClass().toString());
                stats.incTotalJobs();
            }
        }
    }
    
    public void enqueueBlock(List<Job> jobs) throws SchedulerException {
        synchronized (this) {
            for (Job job : jobs) {
                if (logger != null)
                    logger.debug("SchedulerServiceImpl: queuing job " + job.toString());
                job.callAboutToBeEnqueued(this);
                blockedQueue.add(job);
                stats.addWaitingJob(job.getClass().toString());
                stats.incTotalJobs();
            }
        }
        for (Job job : jobs)
            jobDependenciesChanged(job);
    }

    public void dequeue(Job job) {
        synchronized (this) {
            if (!blockedQueue.contains(job) && job.future == null) {
                if (logger != null) {
                    logger.info("SchedulerServiceImpl: job " + job.toString()
                            + " not found in the queue.");
                }
                return;
            }
            job.callAboutToBeDequeued(this);
            blockedQueue.remove(job);
            if (job.future != null) {
            	job.future.cancel(false);
            }
            
            stats.removeWaitingJob(job.getClass().toString());
            stats.decTotalJobs();
        }
        if (logger != null) {
            logger.warn("SchedulerServiceImpl: job " + job.toString()
                    + " not found in the queue.");
        }
    }

    public Job takeJob() throws java.lang.InterruptedException {
    	return null;
    }

    public Job takeJob(Job job) throws SchedulerException {
    	return null;
    }
    
    public void jobStateChanged(Job job, Job.State state) {
        if (logger != null) {
            logger.debug("Job " + job + " changed to state " + state);
        }

        if (state == Job.State.Finished) {
            stats.removeRunJob(job);
            stats.incFinishedJobs();
        } else if (state == Job.State.Running) {
            stats.removeWaitingJob(job.getClass().toString());
            stats.addRunJob(job);
        } else if (state == Job.State.Yielded) {
            stats.removeRunJob(job);
            stats.addWaitingJob(job.getClass().toString());
        } else if (state == Job.State.Error) {

            if (failedQueue.remainingCapacity() == 1)
                failedQueue.remove();
            failedQueue.add(job);
            
            stats.removeRunJob(job);
            stats.addFailedJob(job.getClass().toString());
        }
    }

    public void jobDependenciesChanged(Job job) {
        synchronized (this) {
            if (jobIsQueuedForWork(job) && !job.canExecute()) {
                if (job.future.cancel(false)) {
                	job.future = null;
                	blockedQueue.add(job);                	
                }                
            } else if (job.canExecute()) {
                blockedQueue.remove(job);
                Future<Void> future = executorService.submit(job);
                job.future = future;
            }
        }
    }

    /**
     * Returns whether the job meets its dependencies and is
     * therefore not blocked but queued for work
     */
    public boolean jobIsQueuedForWork(Job job)
    {
    	return (job.future != null && !job.future.isDone());
    }
    
    public void startExecute(int n) {
        executorService = Executors.newCachedThreadPool();
        for(Runnable runnable : frozenJobs)
        {
        	Job job = (Job)runnable;
            Future<Void> future = executorService.submit(job); 
            job.future = future;
        }
        isExecuting = true;
    }

    public void stopExecute() {    	
    	frozenJobs = executorService.shutdownNow();
        isExecuting = false;
    }

    synchronized public boolean isExecuting() {
        return isExecuting; 
    }

    public SchedulerStats getSchedulerStats() {
        return stats;
    }

    public Job[] getFailedQueue() {
        Job[] failedJobs = new Job[failedQueue.size()];
        return failedQueue.toArray(failedJobs);
    }

    public WorkerThread[] getWorkerThreads() {
        return (WorkerThread[]) this.myWorkerThreads.toArray();
    }

    public void startOneShotWorkerThread() {
    }

	@Override
	public void setInitParams(BundleContext bc, Logger l) {
		this.logger = l;
	}

	@Override
	public void shutDown() {
	}

	@Override
	public boolean startUp() {
        
        int numThreads = 2 * Runtime.getRuntime().availableProcessors(); 
        String threadsProperty = System.getProperty(START_THREADS_PROPERTY);
        
        if (threadsProperty != null && !threadsProperty.equals("-1")) {
            try {
                numThreads = Integer.parseInt(threadsProperty);
            } catch (NumberFormatException nfe) {
                logger.warn("Invalid number of threads to start:" + threadsProperty);
            }
        }
        startExecute(numThreads);
        
        String perfLog = System.getProperty(PERF_LOG_PROPERTY);
        if (perfLog != null && perfLog.equals("true")) {
            logger.info("Using performance logging");
            this.perfLog = true;
        }

        return true;
	}

    @Override
    public boolean createAuxQueue(Job j, Deque<Job> jobs, ResumePoint p)
            throws SchedulerException {
        
        if (jobs.isEmpty()) {
            logger.warn("Empty job queue passed to createAuxQueue(). Ignoring request");
            return false;
        }
        
        j.yield(p);
        for (Job job : jobs) {
            j.addDependency(job);
            enqueue(job);
        }
        return true;
    }

    @Override
    public synchronized void yield(Job j, ResumePoint p) throws SchedulerException {
        
        if (j.state() != Job.State.Yielded)
            j.yield(p);

        if (jobIsQueuedForWork(j) && j.future.cancel(false)) {
        	j.future = null;
        	blockedQueue.add(j);                	
        }

    }
}

//vi: ai nosi sw=4 ts=4 expandtab
