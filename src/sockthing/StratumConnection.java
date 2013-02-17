package sockthing;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.PrintStream;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Scanner;
import java.util.Random;

import org.json.JSONObject;
import org.json.JSONArray;

import org.apache.commons.codec.binary.Hex;
import java.nio.ByteBuffer;


public class StratumConnection
{
    private StratumServer server;
    private Socket sock;
    private String connection_id;
    private AtomicLong last_network_action;
    private volatile boolean open;
    private volatile boolean mining_subscribe=false;
    private PoolUser user;
    private Config config;
   
    private byte[] extranonce1;

    private UserSessionData user_session_data;
    
    private AtomicLong next_request_id=new AtomicLong(10000);

    private LinkedBlockingQueue<JSONObject> out_queue = new LinkedBlockingQueue<JSONObject>();
    private Random rnd;
    
    private long get_client_id=-1;
    private String client_version;

    public StratumConnection(StratumServer server, Socket sock, String connection_id)
    {
        this.server = server;
        this.config = server.getConfig();
        this.sock = sock;
        this.connection_id = connection_id;

        open=true;

        last_network_action=new AtomicLong(System.nanoTime());
    
        //Get from user session for now.  Might do something fancy with resume later.
        extranonce1=UserSessionData.getExtranonce1();

        new OutThread().start();
        new InThread().start();

    }

    public void close()
    {
        open=false;
        try
        {
            sock.close();
        }
        catch(Throwable t){}
    }

    public long getLastNetworkAction()
    {
        return last_network_action.get();
    }

    public long getNextRequestId()
    {
        return next_request_id.getAndIncrement();        
    }

    protected void updateLastNetworkAction()
    {
        last_network_action.set(System.nanoTime());
    }

    public void sendMessage(JSONObject msg)
    {
        try
        {
            out_queue.put(msg); 
        }
        catch(java.lang.InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }


    public void sendRealJob(JSONObject block_template, boolean clean)
        throws Exception
    {

        if (user_session_data == null) return;
        if (!mining_subscribe) return;

        String job_id = user_session_data.getNextJobId();

        JobInfo ji = new JobInfo(server, user, job_id, block_template, extranonce1);

        user_session_data.saveJobInfo(job_id, ji);

        JSONObject msg = ji.getMiningNotifyMessage(clean);

        sendMessage(msg);

    }




    public class OutThread extends Thread
    {
        public OutThread()
        {
            setDaemon(true);
        }

        public void run()
        {
            try
            {
                PrintStream out = new PrintStream(sock.getOutputStream());
                while(open)
                {
                    //Using poll rather than take so this thread will
                    //exit if the connection is closed.  Otherwise,
                    //it would wait forever on this queue
                    JSONObject msg = out_queue.poll(30, TimeUnit.SECONDS);
                    if (msg != null)
                    {

                        String msg_str = msg.toString();
                        out.println(msg_str);

                        System.out.println("Out: " + msg.toString());
                        updateLastNetworkAction();
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println(connection_id + ": " + e);
                e.printStackTrace();
            }
            finally
            {
                close();
            }

        }
    }
    public class InThread extends Thread
    {
        public InThread()
        {
            setDaemon(true);
        }

        public void run()
        {
            try
            {
                Scanner scan = new Scanner(sock.getInputStream());

                while(open)
                {
                    String line = scan.nextLine();
                    updateLastNetworkAction();
                    line = line.trim();
                    if (line.length() > 0)
                    {
                        JSONObject msg = new JSONObject(line);
                        System.out.println("In: " + msg.toString());
                        processInMessage(msg);
                    }

                }

            }
            catch(Exception e)
            {
                System.out.println("" + connection_id + ": " + e);
                e.printStackTrace();
            }
            finally
            {
                close();
            }

        }
    }

    private void processInMessage(JSONObject msg)
        throws Exception
    {
        long id = msg.getLong("id");
        if (id == get_client_id)
        {
            client_version = msg.getString("result");
            return;
        }
        
        String method = msg.getString("method");
        if (method.equals("mining.subscribe"))
        {
            JSONObject reply = new JSONObject();
            reply.put("id", id);
            reply.put("error", JSONObject.NULL);
            JSONArray lst2 = new JSONArray();
            lst2.put("mining.notify");
            lst2.put("hhtt");
            JSONArray lst = new JSONArray();
            lst.put(lst2);
            lst.put(Hex.encodeHexString(extranonce1));
            lst.put(4);
            reply.put("result", lst);

            sendMessage(reply);
            mining_subscribe=true;
        }
        else if (method.equals("mining.authorize"))
        {
            JSONArray params = msg.getJSONArray("params");
            String username = (String)params.get(0);
            String password = (String)params.get(1);

            PoolUser pu = server.getAuthHandler().authenticate(username, password);

            JSONObject reply = new JSONObject();
            reply.put("id", id);
            if (pu==null)
            {
                reply.put("error", "unknown user");
                reply.put("result", false);
                sendMessage(reply);
            }
            else
            {
                reply.put("result", true);
                reply.put("error", JSONObject.NULL);
                //reply.put("difficulty", pu.getDifficulty());
                //reply.put("user", pu.getName());
                user = pu;
                sendMessage(reply);
                sendDifficulty();
                sendGetClient();
                user_session_data = server.getUserSessionData(pu);
                sendRealJob(server.getCurrentBlockTemplate(),false);
            }
            
        }
        else if (method.equals("mining.submit"))
        {
            JSONArray params = msg.getJSONArray("params");

            String job_id = params.getString(1);
            JobInfo ji = user_session_data.getJobInfo(job_id);
            if (ji == null)
            {
                JSONObject reply = new JSONObject();
                reply.put("id", id);
                reply.put("result", false);
                reply.put("error", "unknown-work");
                sendMessage(reply);
            }
            else
            {
                SubmitResult res = new SubmitResult();
                res.client_version = client_version;

                ji.validateSubmit(params,res);
                JSONObject reply = new JSONObject();
                reply.put("id", id);

                if (res.our_result.equals("Y"))
                {
                    reply.put("result", true);
                }
                else
                {
                    reply.put("result", false);
                }
                if (res.reason==null)
                {
                    reply.put("error", JSONObject.NULL);
                }
                else
                {
                    reply.put("error", res.reason);
                }
                sendMessage(reply);
            }

        }
    }

    private void sendDifficulty()
        throws Exception
    {
        JSONObject msg = new JSONObject();
        msg.put("id", JSONObject.NULL);
        msg.put("method","mining.set_difficulty");

        JSONArray lst = new JSONArray();
        lst.put(user.getDifficulty());
        msg.put("params", lst);

        sendMessage(msg);
    }

    private void sendGetClient()
        throws Exception
    {
        long id = getNextRequestId();

        get_client_id = id;

        JSONObject msg = new JSONObject();
        msg.put("id", id);
        msg.put("method","client.get_version");

        sendMessage(msg);
        
    }


}
