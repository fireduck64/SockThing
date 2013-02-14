package sockthing;
import java.net.URL;
import java.io.*;
import java.util.*;
import com.google.bitcoin.core.*;

import java.net.URL;
import java.net.URLConnection;
import java.io.DataInputStream;

import org.apache.commons.codec.binary.Hex;

public class PrintTx
{
    public static void main(String args[]) throws Exception
    {
        new PrintTx(args[0]);

    }

    public PrintTx(String hash)
        throws Exception
    {
        Transaction tx = getTransactionFromS3(new Sha256Hash(hash));

        System.out.println(tx);

        for(TransactionInput in : tx.getInputs())
        {
            byte[] b = in.getScriptBytes();
            System.out.println("Input script: "+ Hex.encodeHexString(b));
            //System.out.println("  in: " + in.toString());
        }

        for(TransactionOutput out : tx.getOutputs())
        {
            System.out.println("  out: " + out.toString());
        }


    }

    public static Transaction getTransactionFromS3(Sha256Hash hash)
    {
        try
        {
        Transaction tx;

        String url = "http://s3-us-west-2.amazonaws.com/bitcoin-transactions/" + hash.toString();
        URL u = new URL(url);
        URLConnection conn = u.openConnection();

        DataInputStream in = new DataInputStream(conn.getInputStream());
        int len = conn.getContentLength();
        byte buff[] = new byte[len];
        in.readFully(buff);

        tx = new Transaction(NetworkParameters.prodNet(), buff);

        in.close();
        System.out.println("Got transaction : " + hash + " from s3");

        return tx;
        }
        catch(Exception e)
        {
            System.out.println("S3 get failed");
            e.printStackTrace();
        }

        return null;
    }


}

