
package sockthing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import org.json.JSONObject;
import org.json.JSONArray;

public class BlockUpdater extends Thread
{
    public static final long DB_UPDATE_MS = 60000L;

    private long last_check, last_block;
    private StratumServer server;
    private BitcoinRPC bitcoin_rpc;

    public BlockUpdater(StratumServer server, Config conf)
    {
        setDaemon(true);
        setName("BlockUpdater");

        this.server = server;
        bitcoin_rpc = new BitcoinRPC(conf);
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
                updateBlocks();
                synchronized(this)
                {
                    this.wait(DB_UPDATE_MS);
                }
            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }
        }
    }

    public synchronized void updateBlocks()
        throws java.sql.SQLException, org.json.JSONException, java.io.IOException
    {
        if ( (System.currentTimeMillis() > last_check + DB_UPDATE_MS)
             || (server.getCurrentBlockTemplate().getInt("height") != last_block)
           )
        {  
            Connection conn = null;
            try
            {
                conn = DB.openConnection("share_db");

                // This will only return blocks which have no records in the payments database.
                //
                PreparedStatement ps = conn.prepareStatement("select B.time, B.hash from blocks B left outer join payments P ON B.hash=P.block_hash where B.height IS NOT NULL and B.height < " + server.getCurrentBlockTemplate().getInt("height") + " and P.block_hash IS NULL and B.confirmed=false");
                ResultSet rs = ps.executeQuery();

                while (rs.next())
                {
                    //Int block_height = rs.getInt("height");

                    // We only want to process blocks where the block height is less than the height the server is working on.
                    // This way we don't mark a block as orphan if we have it logged in database faster than the daemon
                    // learns about it. If server is working on block X+1, then a block with height X exists in database.
                    // We could also add this to the mysql query above instead.
                    //
                    // In fact...
                    //if (block_height.compareTo(server.getCurrentBlockTemplate().getInt("height")) >= 0)
                    //{
                    //    contnue;
                    //}
                    // Actually, a block is not confimred for 120. We should wait for confirmation before adding payments.
                    // Also lets us update confirmations number for live on web site.
                    //
                    String hash = rs.getString("hash");
                    String block_time = rs.getString("time");

                    JSONObject post = new JSONObject(bitcoin_rpc.getSimplePostRequest("getblock"));
                    JSONArray params = new JSONArray();
                    params.put(hash);
                    post.put("params", params);

                    // {"id":1,"result":null,"error":{"message":"Block not found","code":-5}} 
                    JSONObject reply = bitcoin_rpc.sendPost(post);

                    if (reply.isNull("result"))
                    {
                        System.out.println("Error fetching block " + hash + ", must be orphan: " + reply.get("error"));
                        PreparedStatement eps = conn.prepareStatement("update blocks set height=NULL where hash=?");
                        eps.setString(1, hash);
                        eps.execute();
                        eps.close();
                        continue;
                    }

                    // Block not null. Check confirmations. If over 120, continue for all payment/etc info. Otherwise update
                    // # of confirmations then exit.

                    JSONObject result = reply.getJSONObject("result");
                    Integer confirmations = result.getInt("confirmations"); 

                    if (confirmations < 120)
                    {
                        if (confirmations == 0)
                        {
                            System.out.println("Block " + hash + ", must be orphan, only has 0 confirms");
                            PreparedStatement eps = conn.prepareStatement("update blocks set height=NULL where hash=?");
                            eps.setString(1, hash);
                            eps.execute();
                            eps.close();
                        }
                        continue;
                    } else {
                        PreparedStatement cps = conn.prepareStatement("update blocks set confirmed=true where hash=?");
                        cps.setString(1, hash);
                        cps.execute();
                        cps.close();
                    }

                    String tx = (String)result.getJSONArray("tx").get(0);

                    post = new JSONObject(bitcoin_rpc.getSimplePostRequest("getrawtransaction"));
                    params = new JSONArray();
                    params.put(tx);
                    params.put(1);
                    post.put("params", params);

                    result = bitcoin_rpc.sendPost(post).getJSONObject("result");
                    JSONArray vout = result.getJSONArray("vout");

                    for(int i=0; i<vout.length(); i++)
                    {
                        JSONObject output = (JSONObject)vout.get(i);
                        PreparedStatement pps = conn.prepareStatement("insert into payments (payment_hash, block_hash, username, payment, time) values (?,?,?,?,?)");
                        pps.setString(1, tx);
                        pps.setString(2, hash);
                        pps.setString(3, (String) output.getJSONObject("scriptPubKey").getJSONArray("addresses").get(0));
                        pps.setInt(4, (int) (output.getDouble("value") * 100000000.0));
                        pps.setString(5, block_time);
                        pps.execute();
                        pps.close();
                        //System.out.println("Value " + (int) (output.getDouble("value") * 100000000.0));
                        //System.out.println("Address " + output.getJSONObject("scriptPubKey").getJSONArray("addresses").get(0));
                    }
                }

                rs.close();
                ps.close();

                last_check = System.currentTimeMillis();
                last_block = server.getCurrentBlockTemplate().getInt("height");
            }
            finally
            {
                DB.safeClose(conn);
            }
    
            //this.notifyAll();
        }
    }

}
