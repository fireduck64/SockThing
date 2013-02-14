package sockthing;
import java.util.StringTokenizer;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;

public class AddressDifficultyAuthHandler implements AuthHandler
{

    public AddressDifficultyAuthHandler(Config config)
    {
        //We don't actually need anything
    }

    /**
     * Return PoolUser object if the user is legit.
     * Return null if the user is unknown/not allowed/incorrect
     */
    public PoolUser authenticate(String username, String password)
    {
        PoolUser pu = new PoolUser(username);

        StringTokenizer stok = new StringTokenizer(username, "_");

        if (stok.countTokens()==2)
        {
            String addr = stok.nextToken();
            int diff = Integer.parseInt(stok.nextToken());
            pu.setName(addr);
            pu.setDifficulty(diff);
            if (!checkAddress(addr)) return null;
            if (diff < 0) return null;
            if (diff > 65536) return null;
            return pu;
        }
        if (stok.countTokens()==1)
        {
            String addr = stok.nextToken();
            pu.setName(addr);
            pu.setDifficulty(32);
            if (!checkAddress(addr)) return null;
            return pu;
        }
        return null;
    }

    public boolean checkAddress(String addr)
    {
        try
        {
            Address a = new Address(NetworkParameters.prodNet(), addr);
            return true;
        }
        catch(Exception e)
        {
            return false;
        }

    }

}
