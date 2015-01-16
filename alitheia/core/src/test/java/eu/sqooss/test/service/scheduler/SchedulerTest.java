package eu.sqooss.test.service.scheduler;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import eu.sqooss.impl.service.db.DBServiceImpl;
import eu.sqooss.impl.service.scheduler.SchedulerServiceImpl;
import eu.sqooss.service.db.DBService;
import eu.sqooss.service.scheduler.Job;
import eu.sqooss.service.scheduler.SchedulerException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class SchedulerTest {
    
    static SchedulerServiceImpl sched;
    
    @Before
    public void setUp() {
    	sched = new SchedulerServiceImpl();
        sched.startExecute(2);
    }

    @Test
    public void testFinishing() throws SchedulerException {

        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        sched.enqueue(j1);
        TestJob j2 = new TestJob(2, "Job 2", dbs);
        sched.enqueue(j2);
        TestJob j3 = new TestJob(3, "Job 3", dbs);
        sched.enqueue(j3);
        TestJob j4 = new TestJob(4, "Job 4", dbs);
        sched.enqueue(j4);
       
               
        try{ //a future object returns as it has been done. So this section ensures that all tasks are executed before going further
            j1.future.get();
            j2.future.get();
            j3.future.get();
            j4.future.get();
        }
        catch(Exception e){
            Assert.fail();
        }
        
        if(sched.getSchedulerStats().getWaitingJobs() !=0)
            Assert.fail();
        
    }
    
    @Test
    public void testWaitingJobs() throws SchedulerException
    {

        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        sched.enqueue(j1);
        TestJob j2 = new TestJob(2, "Job 2", dbs);
        sched.enqueue(j2);
        TestJob j3 = new TestJob(3, "Job 3", dbs);
        sched.enqueue(j3);
        TestJob j4 = new TestJob(4, "Job 4", dbs);
        sched.enqueue(j4);
       
               
        try{ //a future object returns as it has been done. So this section ensures that all tasks are executed before going further
            j1.future.get();
            j2.future.get();
            j3.future.get();
            j4.future.get();
        }
        catch(Exception e){
            Assert.fail();
        }
        
        Assert.assertEquals(Job.State.Finished, j1.state());
        Assert.assertEquals(Job.State.Finished, j2.state());
        Assert.assertEquals(Job.State.Finished, j3.state());
        Assert.assertEquals(Job.State.Finished, j4.state());              
    }
    
    /*@Test
    public void testDequeue() throws SchedulerException {
        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        sched.stopExecute();
        sched.enqueue(j1);
        Assert.assertEquals(Job.State.Queued, j1.state());
        sched.dequeue(j1);
        Assert.assertEquals(Job.State.Created, j1.state());
    }*/
    
    @Test
    public void testSetNoDependensies() throws SchedulerException {
                
        DBService dbs = DBServiceImpl.getInstance();
        
        Assert.assertEquals(0,sched.getSchedulerStats().getTotalJobs());
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        TestJob j2 = new TestJob(2, "Job 2", dbs);
        TestJob j3 = new TestJob(3, "Job 3", dbs);
        TestJob j4 = new TestJob(4, "Job 4", dbs);
        Set<Job> jobs = new HashSet<Job>();
        jobs.add(j1);
        jobs.add(j2);
        jobs.add(j3);
        jobs.add(j4);
        
        sched.enqueueNoDependencies(jobs);
        Assert.assertEquals(4,sched.getSchedulerStats().getTotalJobs());
        
        try{ //a future object returns as it has been done. So this section ensures that all tasks are executed before going further
            j1.future.get();
            j2.future.get();
            j3.future.get();
            j4.future.get();
        }
        catch(Exception e){
            Assert.fail();
        }
        Assert.assertEquals(Job.State.Finished, j1.state());
        Assert.assertEquals(Job.State.Finished, j2.state());
        Assert.assertEquals(Job.State.Finished, j3.state());
        Assert.assertEquals(Job.State.Finished, j4.state());    
        
    }
    
    @Test
    public void enqueueBlockTest(){
        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        TestJob j2 = new TestJob(2, "Job 2", dbs);
        TestJob j3 = new TestJob(3, "Job 3", dbs);
        TestJob j4 = new TestJob(4, "Job 4", dbs);
        List<Job> jobs = new ArrayList<Job>();
        jobs.add(j1);
        jobs.add(j2);
        jobs.add(j3);
        jobs.add(j4);
        
        try{
            sched.enqueueBlock(jobs);
            j1.future.get();
            j2.future.get();
            j3.future.get();
            j4.future.get();
        }
        catch(Exception e)
        {
            Assert.fail();
        }
        Assert.assertEquals(4,sched.getSchedulerStats().getTotalJobs());
        Assert.assertEquals(Job.State.Finished, j1.state());
        Assert.assertEquals(Job.State.Finished, j2.state());
        Assert.assertEquals(Job.State.Finished, j3.state());
        Assert.assertEquals(Job.State.Finished, j4.state());    
    }
    
    @Test
    public void IsExecutingTest() //tests wheter the isExecuting boolean is standard false
    {
        Assert.assertTrue(sched.isExecuting());
        sched.stopExecute();
        Assert.assertFalse(sched.isExecuting());
    }

    //@Test
    //public void 
    
    @After
    public void tearDown() {
        while (sched.getSchedulerStats().getWaitingJobs() > 0)
            try {
                System.out.println(sched.getSchedulerStats().getWaitingJobs());
                System.out.println("AfterClassSleeping");
                Thread.sleep(500);
            } catch (InterruptedException e) {}
            
        sched.stopExecute();
    }
}