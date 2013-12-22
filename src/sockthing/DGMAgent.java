
package sockthing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Collection;
//import java.text.DecimalFormat;
import java.util.Map;
import java.lang.Math;

public class DGMAgent extends Thread
{
    public static final long DB_CHECK_MS = 15000L;
    //public static final long DB_CHECK_MS = 120000L;

    private long last_check, last_block;
    private double dgm_f, dgm_c, dgm_o, dgm_log_s;
    private HashMap<String, Double> raw_map;
    private StratumServer server;

    public DGMAgent(StratumServer server, Config conf)
    {
        setDaemon(true);
        setName("DGMAgent");
        conf.require("dgm_f");
        conf.require("dgm_c");
        conf.require("dgm_o");

        dgm_f = conf.getDouble("dgm_f");
        dgm_c = conf.getDouble("dgm_c");
        dgm_o = conf.getDouble("dgm_o");

        this.server = server;
    }

    // The raw map is the raw scores from database, so they don't need pulled from databsae every time we
    // provide work. However, we do need to customize the scores to reward the worker properly if they hit
    // the share in question. So we adjust the hashmap any time it is needed on the fly, using the
    // info on current user requesting work. How much cpu will this use? Is it scalable? Should we cache
    // a hashmap per miner and only update it when updateUserMap runs or a new block is found?
    //
    // Remember, the raw scores are logs and should be converted back via exp() before returning the map.
    //
    public synchronized HashMap<String, Double> getUserMap(PoolUser pu)
    {
        HashMap<String, Double> payment_map;
        HashMap<String, Double> temp_map;
       
        payment_map = new HashMap<String, Double>(512, 0.5f); 
        temp_map = new HashMap<String, Double>(raw_map);

        String user = pu.getName();
        double dgm_p = ( (double) pu.getDifficulty() ) /  server.getBlockDifficulty();
        double dgm_r = 1.0 + dgm_p * (1.0 - dgm_c) * (1.0 - dgm_o) / dgm_c;
        double dgm_log_r = Math.log(dgm_r);
        double B = ( (double) server.getBlockReward() ) / 100000000.0;

        // If a new miner, add him to the raw list with proper initialized value.
        //
        if (!temp_map.containsKey(user))
        {
            double log_score = dgm_log_s + Math.log(dgm_p * B);
            temp_map.put(user, log_score);
        } else {
            double log_score = dgm_log_s + Math.log(Math.exp(temp_map.get(user) - dgm_log_s) + dgm_p * B);
            temp_map.put(user, log_score);
        }

        // Don't want to change it for real, this is a theoretical found block. Next step on realized block would be:
        // log_s = log_s + log_r;
        //
        double temp_log_s = dgm_log_s + dgm_log_r;

        // loop on all map entries
        for(Map.Entry<String, Double> me : temp_map.entrySet())
        {
            payment_map.put(me.getKey(), (Math.exp(temp_map.get(me.getKey()) - temp_log_s)
                * (dgm_r - 1.0) * (1.0 - dgm_f)) / dgm_p );
        }

        this.notifyAll();
        //System.out.println(payment_map);
        return payment_map;
    }


    /**
     * Do the actual update in this thread to avoid ever blocking work generation
     */
    public void run()
    {
        while(true)
        {
            try
            {
                updateUserMap();
                synchronized(this)
                {
                    this.wait(DB_CHECK_MS/4);
                }
            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }
        }
    }

    public synchronized void updateUserMap()
        throws java.sql.SQLException, org.json.JSONException, java.io.IOException
    {
        if ( (System.currentTimeMillis() > last_check + DB_CHECK_MS) ||
             (server.getCurrentBlockTemplate().getInt("height") != last_block) )
        {  
            long t1_updateUserMap = System.currentTimeMillis();

            Connection conn = null;
            try
            {
                conn = DB.openConnection("share_db");

                PreparedStatement ps = conn.prepareStatement("select username,score from dgm_score");
                ResultSet rs = ps.executeQuery();

                HashMap<String, Double> slice_map = new HashMap<String,Double>(512, 0.5f);

                while (rs.next())
                {
                    String user = rs.getString("username");
                    double log_score = rs.getDouble("score");

                    slice_map.put(user, log_score);
                }

                rs.close();
                ps.close();

                dgm_log_s = slice_map.get("dgm_log_s");
                slice_map.remove("dgm_log_s");

                //System.out.println("Updated DGM map.");
                //System.out.println(" dgm_log_s = " + dgm_log_s + " (" + Math.exp(dgm_log_s) + ")");
                //System.out.println(slice_map);
                // for(Map.Entry<String, Double> me : slice_map.entrySet())
                // {
                //     DecimalFormat df = new DecimalFormat("0.00000000");
                //     System.out.println(me.getKey() +": " +  df.format(me.getValue()));

                // }
                // System.out.println("Total: " + sum(slice_map.values()));
                raw_map = slice_map;
            
                last_check = System.currentTimeMillis();
                last_block = server.getCurrentBlockTemplate().getInt("height");
            }
            finally
            {
                DB.safeClose(conn);
            }
    
            long t2_updateUserMap = System.currentTimeMillis();
            server.getMetricsReporter().metricTime("UpdateUserMapTime", t2_updateUserMap - t1_updateUserMap);
            this.notifyAll();
        }
    }

    public double sum(Collection<Double> vals)
    {
        double x = 0.0;
        for(Double d : vals)
        {
            x+=d;
        }
        return x;
    }
}
