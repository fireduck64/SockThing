package sockthing;
import com.google.bitcoin.core.Sha256Hash;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// import org.json.JSONObject;

public class DBShareSaver implements ShareSaver
{
    private double dgm_f, dgm_c, dgm_o;

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

        config.require("dgm_f");
        config.require("dgm_c");
        config.require("dgm_o");

        dgm_f = config.getDouble("dgm_f");
        dgm_c = config.getDouble("dgm_c");
        dgm_o = config.getDouble("dgm_o");
    }

    public void saveShare(PoolUser pu, SubmitResult submit_result, String source, String unique_job_string, Double block_difficulty, Long block_reward) throws ShareSaveException
    {
        
        Connection conn = null;

        try
        {
            conn = DB.openConnection("share_db");

            PreparedStatement ps = conn.prepareStatement("insert into shares (rem_host, username, our_result, upstream_result, reason, difficulty, hash, client, unique_id, block_difficulty, block_reward) values (?,?,?,?,?,?,?,?,?,?,?)");

            String reason_str = null;
            if (submit_result.reason != null)
            {
                reason_str = submit_result.reason;
                if (reason_str.length() > 50)
                {
                    reason_str = reason_str.substring(0, 50);
                }
                System.out.println("Reason: " + reason_str);
            }
            ps.setString(1, source);
            ps.setString(2, pu.getName());
            ps.setString(3, submit_result.our_result);
            ps.setString(4, submit_result.upstream_result);
            ps.setString(5, reason_str);
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

            ps.setString(9, unique_job_string);
            ps.setDouble(10, block_difficulty);
            ps.setLong(11, block_reward);

            ps.execute();
            ps.close();
           
            if (submit_result.upstream_result != null
                && submit_result.upstream_result.equals("Y")
                && submit_result.hash != null)
            {
                PreparedStatement blockps = conn.prepareStatement("insert into blocks (hash, difficulty, reward, height) values (?,?,?,?)");
              	blockps.setString(1, submit_result.hash.toString());
                blockps.setDouble(2, block_difficulty);
                blockps.setLong(3, block_reward);
                blockps.setInt(4, submit_result.height);

                blockps.execute();
                blockps.close();

                //for(TransactionOutput out : priortx.getOutputs())

            }

            // At some point should there be a CreditMonster abstration layer of some sort?
            //
            if (submit_result.our_result != null
                && submit_result.our_result.equals("Y")
               )
            { 
            //System.out.println("Our result was " + submit_result.our_result);
            double dgm_p = ( (double) pu.getDifficulty() ) /  block_difficulty;
            double dgm_r = 1.0 + dgm_p * (1.0 - dgm_c) * (1.0 - dgm_o) / dgm_c;
            double dgm_log_r = Math.log(dgm_r);
            double dgm_log_o = Math.log(dgm_o);
            double B = ( (double) block_reward ) / 100000000.0;
            double log_score = -10000000;
            double dgm_log_s = 0.0;

            ps = conn.prepareStatement("select username,score from dgm_score where username=? or username=\"dgm_log_s\"");
            ps.setString(1, pu.getName());
            ResultSet rs = ps.executeQuery();

            while (rs.next())
            {
                String result_name = rs.getString("username");

                if (result_name.equals("dgm_log_s"))
                {
                    dgm_log_s = rs.getDouble("score");
                    //System.out.println("DGM setting dgm_log_s to " + dgm_log_s);
                } else {
                    log_score = rs.getDouble("score");
                    //System.out.println("DGM Found score " + Math.exp(log_score) + " for " + result_name);
                }
            }

            ps.close();

            if (dgm_log_s >= 0.0)
            {
                PreparedStatement scoreps = conn.prepareStatement("replace into dgm_score (username, score) values (?, ?)");
                scoreps.setString(1, pu.getName());
                scoreps.setDouble(2, dgm_log_s + Math.log(Math.exp(log_score - dgm_log_s) + dgm_p * B));
                scoreps.execute();
                scoreps.close();

                //System.out.println("DGM score (" + pu.getName() + ") new score " + (dgm_log_s + Math.log(Math.exp(log_score - dgm_log_s) + dgm_p * B)));

                scoreps = conn.prepareStatement("update dgm_score set score=score + ? where username=\"dgm_log_s\"");
                scoreps.setDouble(1, dgm_log_r);
                scoreps.execute();
                scoreps.close();
                //System.out.println("DGM: dgm_score = " + dgm_log_s + " + " + dgm_log_r);
                //System.out.println("DGM: dgm_score = " + Math.exp(dgm_log_s) + " * " + Math.exp(dgm_log_r));

                if (submit_result.upstream_result != null
                    && submit_result.upstream_result.equals("Y")
                    && submit_result.hash != null)
                {
                    scoreps = conn.prepareStatement("update dgm_score set score=score + ? where username!=\"dgm_log_s\"");
                    scoreps.setDouble(1, dgm_log_o);
                    scoreps.execute();
                    scoreps.close();
                }
            } else {
                System.out.println("DGB ERROR: dgm_log_s is below ZERO");
            }
            }
        }
        catch(java.sql.SQLIntegrityConstraintViolationException e)
        {
            System.out.println("Duplicate save - calling good");
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
