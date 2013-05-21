package sockthing;
import java.util.StringTokenizer;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.NetworkParameters;

public class AddressDifficultyAuthHandler implements AuthHandler
{
    protected StratumServer server;

    private int default_difficulty;

    public AddressDifficultyAuthHandler(StratumServer server)
    {
        this.server = server;

        Config config = server.getConfig();

        //if (config.get("default_difficulty") != null && !config.get("default_difficulty").isEmpty())
        if (config.isSet("default_difficulty"))
        {
            int diff = config.getInt("default_difficulty");

            if (diff < 1 || diff > 65536)
            {
                default_difficulty = 32;
                System.out.println("Config default_difficulty " + diff + " invalid. Setting default difficulty to 32.");
            } else {
                default_difficulty = diff;
                System.out.println("Config default_difficulty found. Setting to " + diff);
            }
        } else {
            default_difficulty = 32;
            System.out.println("Config default_difficulty not found. Setting default difficulty to 32.");
        }
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
            if (diff < 1) return null;
            if (diff > 65536) return null;
            return pu;
        }
        if (stok.countTokens()==1)
        {
            String addr = stok.nextToken();
            pu.setName(addr);
            pu.setDifficulty(default_difficulty);
            if (!checkAddress(addr)) return null;
            return pu;
        }
        return null;
    }

    public boolean checkAddress(String addr)
    {
        try
        {
            Address a = new Address(server.getNetworkParameters(), addr);
            return true;
        }
        catch(Exception e)
        {
            return false;
        }

    }

}
