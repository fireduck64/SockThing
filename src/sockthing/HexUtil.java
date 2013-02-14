package sockthing;

import java.nio.ByteBuffer;

import org.apache.commons.codec.binary.Hex;
import java.security.MessageDigest;

import com.google.bitcoin.core.Sha256Hash;
public class HexUtil
{
    public static String getIntAsHex(int n)
    {
        byte[] buff=new byte[4];
        ByteBuffer bb = ByteBuffer.wrap(buff);
        bb.putInt(n);
        return Hex.encodeHexString(buff);

    }

    public static Sha256Hash treeHash(Sha256Hash a, Sha256Hash b)
    {
        try
        {

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(a.getBytes());
            md.update(b.getBytes());

            byte[] pass = md.digest();
            md = MessageDigest.getInstance("SHA-256");
            md.update(pass);
        
            return new Sha256Hash(md.digest());
        }
        catch(java.security.NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }

    }

    public static String swapEndianHexString(String in)
    {
        StringBuilder sb=new StringBuilder();

        for(int i=0; i<in.length(); i+=2)
        {
            String s = in.substring(i,i+2);
            sb.insert(0,s);
        }
        return sb.toString();
    }


    /** What the sweet fuck !?! */
    public static String swapWordHexString(String in)
    {
        StringBuilder sb=new StringBuilder();

        for(int i=0; i<in.length(); i+=8)
        {
            String s = in.substring(i,i+8);
            sb.insert(0,s);
        }
        return sb.toString();
 
    }

    public static String swapBytesInsideWord(String in)
    {   
        StringBuilder sb=new StringBuilder();

        for(int i=0; i<in.length(); i+=8)
        {
            String s = in.substring(i,i+8);
            sb.append(swapEndianHexString(s));
        }
        return sb.toString();

 

    }
    

}
