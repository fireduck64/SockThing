
package sockthing;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;

import java.util.concurrent.LinkedBlockingQueue;
import com.amazonaws.services.cloudwatch.model.*;

import com.amazonaws.auth.BasicAWSCredentials;
import java.util.LinkedList;

public class MetricsReporter extends Thread
{
    StratumServer server;
    AmazonCloudWatchClient cw;

    LinkedBlockingQueue<PutMetricDataRequest> put_queue;



    public MetricsReporter(StratumServer server)
    {
        this.server = server;

        Config conf = server.getConfig();

        put_queue=new LinkedBlockingQueue<PutMetricDataRequest>();

        conf.require("metrics_aws_region");
        conf.require("metrics_aws_key");
        conf.require("metrics_aws_secret");

        cw = new AmazonCloudWatchClient(
            new BasicAWSCredentials(
                conf.get("metrics_aws_key"), conf.get("metrics_aws_secret")));

        cw.setEndpoint("monitoring."+conf.get("metrics_aws_region")+".amazonaws.com");

        setDaemon(true);
        this.start();

    }

    public String getNamespace()
    {
        return "sockthing/" + server.getInstanceId();
    }

    public void metricCount(String name, double count)
    {
        PutMetricDataRequest req = new PutMetricDataRequest();

        LinkedList<MetricDatum> lst = new LinkedList<MetricDatum>();

        MetricDatum md = new MetricDatum();

        md.setMetricName(name);
        md.setValue(count);
        lst.add(md);
        req.setMetricData(lst);
        req.setNamespace(getNamespace());
    
        try
        {
            put_queue.put(req);
        }
        catch(java.lang.InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    

    public void run()
    {
        while(true)
        {
            try
            {
                doRun();
            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }
        }

    }

    private void doRun() throws Exception
    {
        PutMetricDataRequest put = put_queue.take();

        if (put != null)
        {
            cw.putMetricData(put);
            //System.out.println(put);
        }
    }


}
