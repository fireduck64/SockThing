package sockthing;
import java.util.concurrent.atomic.AtomicLong;

import java.util.Random;

public class UserSessionData
{
    
    private LRUCache<String, JobInfo> open_jobs = new LRUCache<String, JobInfo>(20);

    private AtomicLong next_job_id = new AtomicLong(0);
    private byte[] extranonce1;

    public UserSessionData()
    {
        extranonce1=new byte[4];

        Random rnd = new Random();
        rnd.nextBytes(extranonce1);

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

    public byte[] getExtranonce1()
    {
        return extranonce1;
    }

}
