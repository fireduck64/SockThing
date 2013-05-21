package sockthing;

import java.lang.Math;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import java.util.TreeSet;

import java.net.Socket;
import java.net.ServerSocket;

import org.json.JSONObject;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Block;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class StratumServer
{
    private BitcoinRPC bitcoin_rpc;
    private long max_idle_time = 300L * 1000L * 1000L * 1000L;//5 minutes in nanos
    //private long max_idle_time = 3L * 1000L * 1000L * 1000L;//3 seconds

    
    private Map<String, StratumConnection> conn_map=new HashMap<String, StratumConnection>(1024, 0.5f);

    private Config config;
    private AuthHandler auth_handler;
    private NetworkParameters network_params;
    private ShareSaver share_saver;
    private OutputMonster output_monster;
    private MetricsReporter metrics_reporter;
    private WittyRemarks witty_remarks;
    private PPLNSAgent pplns_agent;

    private String instance_id;

    /**
     * Nothing should read this, anything interested should call getCurrentBlockTemplate() instead
     */
    private JSONObject cached_block_template;
    
    private Map<String, UserSessionData> user_session_data_map=new HashMap<String, UserSessionData>(1024, 0.5f);

    private volatile int current_block;
    private volatile long current_block_update_time;

    private Semaphore new_block_notify_object;

    private volatile double block_difficulty;

    private volatile long block_reward;
    private StratumServer server;

    public StratumServer(Config config)
    {
        new_block_notify_object = new Semaphore(0);

        this.config = config;

        config.require("port");

        bitcoin_rpc = new BitcoinRPC(config);

        server = this;

    }
    public void start()
    {

        new TimeoutThread().start();
        new NewBlockThread().start();
        new PruneThread().start();

        List<String> ports = config.getList("port");
        for(String s : ports)
        {
            int port = Integer.parseInt(s);
            new ListenThread(port).start();
        }

        if (witty_remarks != null)
        {
            witty_remarks.start();
        }

        //pplns_agent.start();


    }

    public void setAuthHandler(AuthHandler auth_handler)
    {
        this.auth_handler = auth_handler;
    }
    public AuthHandler getAuthHandler()
    {
        return auth_handler;
    }

    public void setMetricsReporter(MetricsReporter mr)
    {
        this.metrics_reporter = mr;
    }
    public MetricsReporter getMetricsReporter()
    {
        return metrics_reporter;
    }

    public Config getConfig()
    {
        return config;
    }

    public Double getBlockDifficulty()
    {
        return block_difficulty;
    }

    public Long getBlockReward()
    {
        return block_reward;
    }

    public String getInstanceId()
    {
        return instance_id;
    }
    public void setInstanceId(String instance_id)
    {
        this.instance_id = instance_id;
    }

    public void setShareSaver(ShareSaver share_saver)
    {
        this.share_saver = share_saver;
    }
    public ShareSaver getShareSaver()
    {
        return share_saver;
    }

    public void setOutputMonster(OutputMonster output_monster)
    {
        this.output_monster = output_monster;
    }
    public OutputMonster getOutputMonster()
    {
        return output_monster;
    }

    public void setWittyRemarks(WittyRemarks remarks)
    {
        this.witty_remarks = remarks;
    }
    public WittyRemarks getWittyRemarks()
    {
        return witty_remarks;
    }

    public void setPPLNSAgent(PPLNSAgent pplns_agent)
    {
        this.pplns_agent = pplns_agent;
    }
    public PPLNSAgent getPPLNSAgent()
    {
        return pplns_agent;
    }

    public NetworkParameters getNetworkParameters(){return network_params;}

    public void setNetworkParameters(NetworkParameters network_params)
    {
        this.network_params = network_params;
    }

    public class ListenThread extends Thread
    {
        private int port;
        public ListenThread(int port)
        {
            this.port = port;
            setName("Listen:"+port);
        }


        public void run()
        {
            System.out.println("Listening on port: " + port);

            try
            {
                ServerSocket ss = new ServerSocket(port, 256);
                ss.setReuseAddress(true);


                while(ss.isBound())
                {
                    try
                    {
                        Socket sock = ss.accept();

                        String id = UUID.randomUUID().toString();

                        StratumConnection conn = new StratumConnection(server, sock, id);
                        synchronized(conn_map)
                        {
                            conn_map.put(id, conn);
                        }
                    }
                    catch(Throwable t)
                    {
                        t.printStackTrace();
                    }

                }
            }
            catch(java.io.IOException e)
            {
                throw new RuntimeException(e);
            }

        }
    }

    public class TimeoutThread extends Thread
    {
        public TimeoutThread()
        {
            setName("TimeoutThread");
            setDaemon(true);
        }

        public void run()
        {   
            while(true)
            {   
                LinkedList<Map.Entry<String, StratumConnection> > lst= new LinkedList<Map.Entry<String, StratumConnection> >();

                synchronized(conn_map)
                {
                    lst.addAll(conn_map.entrySet());
                }
                getMetricsReporter().metricCount("connections", lst.size());

                for(Map.Entry<String, StratumConnection> me : lst)
                {
                    if (me.getValue().getLastNetworkAction() + max_idle_time < System.nanoTime())
                    {
                        System.out.println("Closing connection due to inactivity: " + me.getKey());
                        me.getValue().close();
                        synchronized(conn_map)
                        {
                            conn_map.remove(me.getKey());
                        }
                    }   
                }

                try{Thread.sleep(30000);}catch(Throwable t){}

                

            }

        }

    }

    /**
     * Prunes jobs out of user_session_data_map
     */
    public class PruneThread extends Thread
    {
        public PruneThread()
        {
            setName("PruneThread");
            setDaemon(true);

        }
        public void run()
        {
            while(true)
            {
                try
                {
                    Thread.sleep(43000);
                    doRun();
                }
                catch(Throwable t)
                {           
                    t.printStackTrace();
                }
            }

        }

        private void doRun()
            throws Exception
        {
            TreeSet<String> to_delete = new TreeSet<String>();
            int user_sessions=0;
            int user_jobs=0;
            synchronized(user_session_data_map)
            {
                user_sessions = user_session_data_map.size();

                for(Map.Entry<String, UserSessionData> me : user_session_data_map.entrySet())
                {
                    user_jobs += me.getValue().getJobCount();

                    if (me.getValue().prune())
                    {
                        to_delete.add(me.getKey());
                    }
                }

                for(String id : to_delete)
                {
                    user_session_data_map.remove(id);
                }


            }

            metrics_reporter.metricCount("usersessions", user_sessions);
            metrics_reporter.metricCount("userjobs", user_jobs);

        }
    }

    /**
     * 0 - not stale (current)
     * 1 - slightly stale
     * 2 - really stale
     */
    public int checkStale(int next_block)
    {
        if (next_block == current_block + 1)
        {
            return 0;
        }
        if (next_block == current_block)
        {
            if (current_block_update_time + 10000 > System.currentTimeMillis())
            {
                return 1;
            }
        }
        return 2;

        
    }

    public class NewBlockThread extends Thread
    {
        int last_block;
        long last_update_time;
        long last_success_time;

        public static final long MAX_TIME_WITHOUT_SUCCESS=2L*60L*1000L; //2 minutes
        public static final long TEMPLATE_REFRESH_TIME=30L * 1000L; //30 seconds

        public NewBlockThread()
        {
            setDaemon(true);
            setName("NewBlockThread");
            last_update_time=System.currentTimeMillis();
            last_success_time=System.currentTimeMillis();
            
        }

        public void run()
        {
            while(true)
            {
                try
                {
                    if (last_success_time + MAX_TIME_WITHOUT_SUCCESS < System.currentTimeMillis())
                    {
                        System.out.println("MAX_TIME_WITHOUT_SUCCESS EXCEEDED.  Giving up.  Failure.");
                        System.exit(-1);
                    }
                    if (new_block_notify_object.tryAcquire(1, 1000, TimeUnit.MILLISECONDS))
                    {
                        System.out.println("New block notify");
                    }
                    doRun();

                }
                catch(Throwable t)
                {
                    t.printStackTrace();
                }

            }

        }
        private void doRun()throws Exception
        {


            JSONObject reply = bitcoin_rpc.doSimplePostRequest("getblockcount");

            int block_height = reply.getInt("result");

            if (block_height != last_block)
            {
                System.out.println(reply);
                triggerUpdate(true);
                last_block = block_height;
                last_update_time = System.currentTimeMillis();
                last_success_time = System.currentTimeMillis();

                current_block_update_time = System.currentTimeMillis();
                current_block = block_height;

            }

            if (last_update_time + TEMPLATE_REFRESH_TIME < System.currentTimeMillis())
            {
                
                triggerUpdate(false);
                last_update_time = System.currentTimeMillis();
                last_success_time = System.currentTimeMillis();

            }

        }
    }

    public void notifyNewBlock()
    {
        new_block_notify_object.release(1);
    }

    private double difficultyFromHex(Long bits)
    {
        double max_body = Math.log(0x00ffff), scaland = Math.log(256);
        return Math.exp(max_body - Math.log(bits & 0x00ffffff) + scaland * (0x1d - ((bits & 0xff000000) >> 24)));
    }

    private void updateBlockReward()
        throws Exception
    {
        block_reward =  getCurrentBlockTemplate().getLong("coinbasevalue");
    }
    
    private void updateBlockDifficulty()
        throws Exception
    {
        String hexString = "0x" + getCurrentBlockTemplate().getString("bits");
        Long hexInt = Long.decode(hexString).longValue();

        block_difficulty = difficultyFromHex(hexInt);
    }

    private void triggerUpdate(boolean clean)
        throws Exception
    {
        System.out.println("Update triggered. Clean: " + clean);

        cached_block_template = null;

        long t1_get_block = System.currentTimeMillis();
        JSONObject block_template = getCurrentBlockTemplate();
        long t2_get_block = System.currentTimeMillis();

        getMetricsReporter().metricTime("GetBlockTemplateTime", t2_get_block - t1_get_block);

        if (clean)
        {
            // Needs the new block template cached before we update.
            updateBlockReward();
            updateBlockDifficulty();
        }

        long t1_update_connection = System.currentTimeMillis();

        LinkedList<Map.Entry<String, StratumConnection> > lst= new LinkedList<Map.Entry<String, StratumConnection> >();
        synchronized(conn_map)
        {
            lst.addAll(conn_map.entrySet());
        }

        for(Map.Entry<String, StratumConnection> me : lst)
        {
            me.getValue().sendRealJob(block_template, clean);

        }
        
        long t2_update_connection = System.currentTimeMillis();

        getMetricsReporter().metricTime("UpdateConnectionsTime", t2_update_connection - t1_update_connection);

    }


    public static void main(String args[]) throws Exception
    {
        if (args.length != 1)
        {
            System.out.println("Expected exactly one argument, a config file");
            System.out.println("java -jar SockThing.jar pool.cfg");
            return;
        }

        Config conf = new Config(args[0]);

        conf.require("pay_to_address");
        conf.require("network");
        conf.require("instance_id");
        conf.require("coinbase_text");
        conf.require("saver_messaging_enabled");
        conf.require("witty_remarks_enabled");
        
        StratumServer server = new StratumServer(conf);

        server.setInstanceId(conf.get("instance_id"));
        server.setMetricsReporter(new MetricsReporter(server));

        server.setAuthHandler(new AddressDifficultyAuthHandler(server));

        if (conf.getBoolean("saver_messaging_enabled"))
        {
            server.setShareSaver(new ShareSaverMessaging(server, new DBShareSaver(conf)));
        }
        else
        {
            server.setShareSaver(new DBShareSaver(conf));
        }


        String network = conf.get("network");
        if (network.equals("prodnet"))
        {
            server.setNetworkParameters(NetworkParameters.prodNet());
        }
        else if (network.equals("testnet"))
        {
            server.setNetworkParameters(NetworkParameters.testNet3());
        }
        
        server.setOutputMonster(new OutputMonsterShareFees(conf, server.getNetworkParameters()));

        if (conf.getBoolean("witty_remarks_enabled"))
        {
            server.setWittyRemarks(new WittyRemarks());
        }
        //server.setPPLNSAgent(new PPLNSAgent(server));

        new NotifyListenerUDP(server).start();        
        server.start();
    }


    public JSONObject getCurrentBlockTemplate()
        throws java.io.IOException, org.json.JSONException
    {
        JSONObject c = cached_block_template;
        if (c != null) return c;

        JSONObject post;
        post = new JSONObject(bitcoin_rpc.getSimplePostRequest("getblocktemplate"));
        c = bitcoin_rpc.sendPost(post).getJSONObject("result");

        cached_block_template=c;

        getMetricsReporter().metricCount("getblocktemplate",1.0);
        return c;

    }

    public double getDifficulty()
        throws java.io.IOException, org.json.JSONException
    {
        JSONObject post;
        post = new JSONObject(bitcoin_rpc.getSimplePostRequest("getdifficulty"));
        return bitcoin_rpc.sendPost(post).getDouble("result");
    }

    public String submitBlock(Block blk)
    {
        String result="N";
        for(int i=0; i<10; i++)
        {
            System.out.println("Attempting block submit");
            result = submitBlockAttempt(blk);
            if(result.equals("Y")) return result;
        }
        return result;
    }


    private String submitBlockAttempt(Block blk)
    {
        try
        {
            JSONObject result = bitcoin_rpc.submitBlock(blk);

            System.out.println("Block result: " + result.toString(2));

            if (result.isNull("error") && result.isNull("result"))
            {
                return "Y"; 
            }
            else
            {
                System.out.println("Block submit error:  "+ result.get("error"));
                return "N";
            }

        }
        catch(Throwable t)
        {
            t.printStackTrace();
            return "N";
        }
        
    }

    public UserSessionData getUserSessionData(PoolUser pu)
    {
        synchronized(user_session_data_map)
        {
            UserSessionData ud = user_session_data_map.get(pu.getWorkerName());
            if (ud == null) ud = new UserSessionData(this);
            user_session_data_map.put(pu.getWorkerName(), ud);
            return ud;

        }

    }

}
