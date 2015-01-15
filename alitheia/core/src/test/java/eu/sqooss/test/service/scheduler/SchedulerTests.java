package eu.sqooss.test.service.scheduler;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import eu.sqooss.impl.service.db.DBServiceImpl;
import eu.sqooss.impl.service.scheduler.SchedulerServiceImpl;
import eu.sqooss.service.db.DBService;
import eu.sqooss.service.scheduler.Job;
import eu.sqooss.service.scheduler.SchedulerException;
import org.junit.Assert;

public class SchedulerTests {
    
    static SchedulerServiceImpl sched;
    
    @BeforeClass
    public static void setUp() {
    	sched = new SchedulerServiceImpl();
        sched.startExecute(2);
    }

    @Test
    public void testJobYield() throws SchedulerException {

        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        sched.enqueue(j1);
        TestJob j2 = new TestJob(2, "Job 2", dbs);
        sched.enqueue(j2);
        TestJob j3 = new TestJob(3, "Job 3", dbs);
        sched.enqueue(j3);
        TestJob j4 = new TestJob(4, "Job 4", dbs);
        sched.enqueue(j4);
        
        int timeout = 0;
        while (sched.getSchedulerStats().getWaitingJobs() > 0)
        {
            try 
            {
                Thread.sleep(200);
                timeout += 1;
                if(timeout==20)
                    Assert.fail();
            }
            catch(Exception e) {
                Assert.fail(null);
            }
        }
        
        Assert.assertEquals(Job.State.Finished, j1.state());
        Assert.assertEquals(Job.State.Finished, j2.state());
        Assert.assertEquals(Job.State.Finished, j3.state());
        Assert.assertEquals(Job.State.Finished, j4.state());
        
    }
    
    @Test
    public void testDequeue() throws SchedulerException {
        
        DBService dbs = DBServiceImpl.getInstance();
        
        TestJob j1 = new TestJob(1, "Job 1", dbs);
        sched.stopExecute();
        sched.enqueue(j1);
        Assert.assertEquals(Job.State.Queued, j1.state());
        sched.dequeue(j1);
        Assert.assertEquals(Job.State.Created, j1.state());
        sched.startExecute(2); //restart
    }
    
    @AfterClass
    public static void tearDown() {
        while (sched.getSchedulerStats().getWaitingJobs() > 0)
            try {
                System.out.println("test");
                Thread.sleep(500);
            } catch (InterruptedException e) {}
            
        sched.stopExecute();
    }
}