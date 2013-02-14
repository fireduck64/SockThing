package sockthing;
import java.util.concurrent.atomic.AtomicLong;

import java.util.Random;

public class UserSessionData
{
    
    /**
     * Hopefully the user is submitting shares and this keeping the interesting 
     * jobs in memory.
     */
    private LRUCache<String, JobInfo> open_jobs = new LRUCache<String, JobInfo>(25);

    private AtomicLong next_job_id = new AtomicLong(0);

    public UserSessionData()
    {
    }

    public JobInfo getJobInfo(String job_id)
    {
        synchronized(open_jobs)
        {
            return open_jobs.get(job_id);
        }
    }

    public String getNextJobId()
    {
        return "job_" + next_job_id.getAndIncrement();
    }

    public void saveJobInfo(String job_id, JobInfo ji)
    {
        synchronized(open_jobs)
        {
            open_jobs.put(job_id, ji);
        }
    }

    public static byte[] getExtranonce1()
    {
        return "SOCK".getBytes();
    }

}
