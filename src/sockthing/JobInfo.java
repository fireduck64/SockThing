package sockthing;

import org.json.JSONObject;
import org.json.JSONArray;

import com.google.bitcoin.core.Coinbase;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Block;
import java.math.BigInteger;

import org.apache.commons.codec.binary.Hex;

import com.google.bitcoin.core.Sha256Hash;
import java.util.ArrayList;
import java.util.LinkedList;
import java.security.MessageDigest;
import java.util.HashSet;

public class JobInfo
{
    private NetworkParameters network_params;
    private StratumServer server;
    private String job_id;
    private JSONObject block_template;
    private byte[] extranonce1;
    private PoolUser pool_user;
    private HashSet<String> submits;
    private Sha256Hash share_target;
    private double difficulty;
    private long value;

    private Coinbase coinbase;

    public JobInfo(StratumServer server, PoolUser pool_user, String job_id, JSONObject block_template, byte[] extranonce1)
        throws org.json.JSONException
    {
        this.pool_user = pool_user;
        this.server = server;
        this.network_params = network_params;
        this.job_id = job_id;
        this.block_template = block_template;
        this.extranonce1 = extranonce1;


        this.value = block_template.getLong("coinbasevalue");
        this.difficulty = server.getBlockDifficulty();

        int height = block_template.getInt("height");

        submits = new HashSet<String>();


        coinbase = new Coinbase(server, pool_user, height, BigInteger.valueOf(value), getFeeTotal(), extranonce1);

        share_target = DiffMath.getTargetForDifficulty(pool_user.getDifficulty());

    }

    public int getHeight()
        throws org.json.JSONException
    {
        return block_template.getInt("height");
    }

    private BigInteger getFeeTotal()
        throws org.json.JSONException
    {
        long fee_total = 0;
        JSONArray transactions = block_template.getJSONArray("transactions");

        for(int i=0; i<transactions.length(); i++)
        {
            JSONObject tx = transactions.getJSONObject(i);

            long fee = tx.getLong("fee");
            fee_total += fee;
        }

        return BigInteger.valueOf(fee_total);
    }

    public JSONObject getMiningNotifyMessage(boolean clean)
        throws org.json.JSONException
    {
        JSONObject msg = new JSONObject();
        msg.put("id", JSONObject.NULL);
        msg.put("method", "mining.notify");

        JSONArray roots= new JSONArray();
        /*for(int i=0; i<5; i++)
        {
            byte[] root = new byte[32];
            rnd.nextBytes(root);
            roots.put(Hex.encodeHexString(root));
        }*/


        String protocol="00000002";
        String diffbits=block_template.getString("bits");
        int ntime = (int)System.currentTimeMillis()/1000;
        String ntime_str= HexUtil.getIntAsHex(ntime);

        JSONArray params = new JSONArray();

        params.put(job_id);
        params.put(HexUtil.swapBytesInsideWord(HexUtil.swapEndianHexString(block_template.getString("previousblockhash")))); //correct
        params.put(Hex.encodeHexString(coinbase.getCoinbase1())); 
        params.put(Hex.encodeHexString(coinbase.getCoinbase2()));
        params.put(getMerkleRoots());
        params.put(protocol); //correct
        params.put(block_template.getString("bits")); //correct
        params.put(HexUtil.getIntAsHex(block_template.getInt("curtime"))); //correct
        params.put(clean);
        msg.put("params", params);

        return msg;
    }

    public void validateSubmit(JSONArray params, SubmitResult submit_result)
    {
        String unique_id = HexUtil.sha256(params.toString());

        try
        {
            validateSubmitInternal(params, submit_result);
     

        }
        catch(Throwable t)
        {
            submit_result.our_result="N";
            submit_result.reason="Exception: " + t;
        }
        finally
        {
            try
            {
                server.getShareSaver().saveShare(pool_user,submit_result, "sockthing/" + server.getInstanceId(), unique_id, difficulty, value);
            }
            catch(ShareSaveException e)
            {

                submit_result.our_result="N";
                submit_result.reason="Exception: " + e;
            }

        }
        
    }

    public void validateSubmitInternal(JSONArray params, SubmitResult submit_result)
        throws org.json.JSONException, org.apache.commons.codec.DecoderException, ShareSaveException
    {
        String user = params.getString(0);
        String job_id = params.getString(1);
        byte[] extranonce2 = Hex.decodeHex(params.getString(2).toCharArray());
        String ntime = params.getString(3);
        String nonce = params.getString(4);


        String submit_cannonical_string = params.getString(2).trim().toLowerCase(); 

        synchronized(submits)
        {
            if (submits.contains(submit_cannonical_string))
            {
                submit_result.our_result="N";
                submit_result.reason="duplicate";
                return;
            }
            submits.add(submit_cannonical_string);
        }

        int stale = server.checkStale(getHeight());
        if (stale >= 2)
        {
            submit_result.our_result="N";
            submit_result.reason="quite stale";
            return;
        }
        if (stale==1)
        {
            submit_result.reason="slightly stale";
        }


        //nonce = HexUtil.swapEndianHexString(nonce);

        //System.out.println("nonce: " + nonce);
        //System.out.println("extra2: " + params.getString(2));


        /*extranonce2[0]=1;
        extranonce2[1]=0;
        extranonce2[2]=0;
        extranonce2[3]=0;
        nonce="00000000";*/

        Sha256Hash coinbase_hash;
        synchronized(coinbase)
        {
            coinbase.setExtranonce2(extranonce2);
            coinbase_hash = coinbase.genTx().getHash();
 

            Sha256Hash merkle_root = new Sha256Hash(HexUtil.swapEndianHexString(coinbase_hash.toString()));
        
            JSONArray branches = getMerkleRoots();
            for(int i=0; i<branches.length(); i++)
            {
                Sha256Hash br= new Sha256Hash(branches.getString(i));
                //System.out.println("Merkle " + merkle_root + " " + br);
                merkle_root = HexUtil.treeHash(merkle_root, br);
            }

            try
            {
                StringBuilder header = new StringBuilder();
                header.append("00000002");
                header.append(HexUtil.swapBytesInsideWord(HexUtil.swapEndianHexString(block_template.getString("previousblockhash"))));
                header.append(HexUtil.swapBytesInsideWord(merkle_root.toString()));
                header.append(ntime);
                header.append(block_template.getString("bits"));
                header.append(nonce);
                //header.append("000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000");

                String header_str = header.toString();

                header_str = HexUtil.swapBytesInsideWord(header_str);
                System.out.println("Header: " + header_str);
                System.out.println("Header bytes: " + header_str.length());

                //header_str = HexUtil.swapWordHexString(header_str);

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(Hex.decodeHex(header_str.toCharArray()));

                byte[] pass = md.digest();
                md.reset();
                md.update(pass);

                Sha256Hash blockhash = new Sha256Hash(HexUtil.swapEndianHexString(new Sha256Hash(md.digest()).toString()));
                System.out.println("Found block hash: " + blockhash);
                submit_result.hash = blockhash;

                if (blockhash.toString().compareTo(share_target.toString()) < 0)
                {
                    submit_result.our_result="Y";

                    server.getEventLog().log("Share " + pool_user.getName() + " " + getHeight() + " " + blockhash );
                }
                else
                {
                    submit_result.our_result="N";
                    submit_result.reason="H-not-zero";
                    return;
                }
                String upstream_result=null;
                if (blockhash.toString().compareTo(block_template.getString("target")) < 0)
                {
                    submit_result.upstream_result
                    = buildAndSubmitBlock(params, merkle_root);
                    submit_result.height = getHeight();
                }

                

            }
            catch(java.security.NoSuchAlgorithmException e)
            {
                throw new RuntimeException(e);
            }

        }

    }

    public String buildAndSubmitBlock(JSONArray params, Sha256Hash merkleRoot)
        throws org.json.JSONException, org.apache.commons.codec.DecoderException
    {
        System.out.println("WE CAN BUILD A BLOCK.  WE HAVE THE TECHNOLOGY.");

        String user = params.getString(0);
        String job_id = params.getString(1);
        byte[] extranonce2 = Hex.decodeHex(params.getString(2).toCharArray());
        String ntime = params.getString(3);
        String nonce = params.getString(4);

        long time = Long.parseLong(ntime,16);
        long target = Long.parseLong(block_template.getString("bits"),16);
        long nonce_l = Long.parseLong(nonce,16);

        LinkedList<Transaction> lst = new LinkedList<Transaction>();

        lst.add(coinbase.genTx());
        JSONArray transactions = block_template.getJSONArray("transactions");

        for(int i=0; i<transactions.length(); i++)
        {
            JSONObject tx = transactions.getJSONObject(i);
            try
            {
                Transaction tx_obj = new Transaction(network_params, Hex.decodeHex(tx.getString("data").toCharArray()));
                lst.add(tx_obj);
            }
            catch(com.google.bitcoin.core.ProtocolException e)
            {
                throw new RuntimeException(e);
            }
        }
 



        Block block = new Block(
            network_params, 
            2, 
            new Sha256Hash(block_template.getString("previousblockhash")),
            new Sha256Hash(HexUtil.swapEndianHexString(merkleRoot.toString())),
            time,
            target,
            nonce_l,
            lst);

        System.out.println("Constructed block hash: " + block.getHash());


        try
        {
            block.verifyTransactions();
            System.out.println("Block VERIFIED");
            byte[] blockbytes = block.bitcoinSerialize();
            System.out.println("Bytes: " + blockbytes.length);

            String ret =  server.submitBlock(block);

            if (ret.equals("Y"))
            {
                coinbase.markRemark();
            }
            
            server.getEventLog().log("BLOCK SUBMITTED: "+ getHeight() + " " + block.getHash() );

            return ret;


        }
        catch(com.google.bitcoin.core.VerificationException e)
        {
            e.printStackTrace();
            return "N";
        }
    }
    
    public JSONArray getMerkleRoots()
        throws org.json.JSONException
    {
        ArrayList<Sha256Hash> hashes = new ArrayList<Sha256Hash>();

        JSONArray transactions = block_template.getJSONArray("transactions");


        for(int i=0; i<transactions.length(); i++)
        {
            JSONObject tx = transactions.getJSONObject(i);
            Sha256Hash hash = new Sha256Hash(HexUtil.swapEndianHexString(tx.getString("hash")));
            hashes.add(hash);
        }
        
        JSONArray roots = new JSONArray();

        while(hashes.size() > 0)
        {
            ArrayList<Sha256Hash> next_lst = new ArrayList<Sha256Hash>();
            roots.put(hashes.get(0).toString());

            for(int i=1; i<hashes.size(); i+=2)
            {
                if (i+1==hashes.size())
                {
                    next_lst.add(HexUtil.treeHash(hashes.get(i), hashes.get(i)));
                }
                else
                {
                    next_lst.add(HexUtil.treeHash(hashes.get(i), hashes.get(i+1)));
                }
            }
            hashes=next_lst;

        }

        return roots;

    } 



    
}
