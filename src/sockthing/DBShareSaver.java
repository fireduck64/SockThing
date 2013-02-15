package sockthing;
import com.google.bitcoin.core.Sha256Hash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DBShareSaver implements ShareSaver
{
    public DBShareSaver(Config config)
        throws java.sql.SQLException
    {
        config.require("share_db_driver");
        config.require("share_db_uri");
        config.require("share_db_username");
        config.require("share_db_password");

        DB.openConnectionPool(
            "share_db",
            config.get("share_db_driver"),
            config.get("share_db_uri"),
            config.get("share_db_username"),
            config.get("share_db_password"),
            64,
            16);

    }

    public void saveShare(PoolUser pu, JobInfo ji, SubmitResult submit_result, String source) throws ShareSaveException
    {
        
        Connection conn = null;

        try
        {
            conn = DB.openConnection("share_db");

            PreparedStatement ps = conn.prepareStatement("insert into shares (rem_host, username, our_result, upstream_result, reason, difficulty, hash, client) values (?,?,?,?,?,?,?,?)");

            ps.setString(1, source);
            ps.setString(2, pu.getName());
            ps.setString(3, submit_result.our_result);
            ps.setString(4, submit_result.upstream_result);
            ps.setString(5, submit_result.reason);
            ps.setDouble(6, pu.getDifficulty());

            if (submit_result.hash != null)
            {
                ps.setString(7, submit_result.hash.toString());
            }
            else
            {
                ps.setString(7, null);
            }
            ps.setString(8, submit_result.client_version);

            ps.execute();
            ps.close();
            
        }
        catch(java.sql.SQLException e)
        {
            throw new ShareSaveException(e);
        }
        finally
        {
            DB.safeClose(conn);
        }


        
        
        
    
    }



}
