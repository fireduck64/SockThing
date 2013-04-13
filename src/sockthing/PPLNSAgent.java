
package sockthing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Collection;
import java.text.DecimalFormat;
import java.util.Map;


/**
 * NOTE: this is not what HHTT currently uses for PPLNS calculation.
 * It is very similar but because of the very small payments that could end up
 * going to miners I thought it was better to do the PPLNS credits outside of the coinbase
 * transaction and use the existing HHTT payment rules so that miners don't get a bunch 
 * of very small payments
 */
public class PPLNSAgent extends Thread
{
    public static final long DB_CHECK_MS = 120000L;

    private long last_check;
    private HashMap<String, Double> last_map;
    private StratumServer server;

    public PPLNSAgent(StratumServer server)
    {
        setDaemon(true);
        setName("PPLNSAgent");

        this.server = server;

    }


    public synchronized HashMap<String, Double> getUserMap()
    {
        this.notifyAll();
        return last_map;
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

    private void updateUserMap()
        throws java.sql.SQLException, org.json.JSONException, java.io.IOException
    {
        if (System.currentTimeMillis() > last_check + DB_CHECK_MS)
        {  
            Connection conn = null;
            try
            {
                double network_diff = server.getDifficulty();
                double diff1shares = 0.0;

                conn = DB.openConnection("share_db");

                PreparedStatement ps = conn.prepareStatement("select * from shares where our_result='Y' order by time desc limit ?");
                ps.setLong(1, Math.round(network_diff/32.0));
                ResultSet rs = ps.executeQuery();

                HashMap<String, Double> slice_map = new HashMap<String,Double>(512, 0.5f);

                while ((rs.next()) && (diff1shares + 1e-3 < network_diff))
                {
                    String user = rs.getString("username");
                    long share_diff = rs.getLong("difficulty");
                    double apply_diff = Math.min(share_diff, network_diff - diff1shares);

                    /*System.out.println("Diffs:" + 
                        " share " + share_diff +
                        " apply " + apply_diff +
                        " network " + network_diff +
                        " shares " + diff1shares);*/

                         

                    diff1shares+=apply_diff;

                    double fee = 0.0175+(0.1325/Math.pow(share_diff,0.6));
                    fee=0.0;
                    double slice = 25.0 *(1.0-(fee))*apply_diff/network_diff;

                    if (!slice_map.containsKey(user))
                    {
                        slice_map.put(user, slice);
                    }
                    else
                    {
                        slice_map.put(user, slice + slice_map.get(user));
                    }


                    
                }
                rs.close();
                ps.close();

                //System.out.println(slice_map);
                for(Map.Entry<String, Double> me : slice_map.entrySet())
                {
                    DecimalFormat df = new DecimalFormat("0.00000000");
                    System.out.println(me.getKey() +": " +  df.format(me.getValue()));

                }
                System.out.println("Total: " + sum(slice_map.values()));
                last_map = slice_map;
            
            
                last_check=System.currentTimeMillis();
            }
            finally
            {
                DB.safeClose(conn);
            }

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
