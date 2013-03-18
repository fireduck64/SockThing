
package sockthing;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

/**
 * Optional database of witty remarks to be injected into Coinbase transactions
 *
 * Assumes using a table from the sharedb.
 */
public class WittyRemarks extends Thread
{
    public static final long DB_CHECK_MS = 120000L;

    private long last_check;
    private String last_remark;

    public WittyRemarks()
    {
        setDaemon(true);
        setName("WittyRemarks");

    }

    public synchronized String getNextRemark()
    {

        this.notifyAll();
        return last_remark;
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
                updateLastRemark();
                synchronized(this)
                {
                    this.wait(DB_CHECK_MS/4);
                }
            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }
        }



    }

    private void updateLastRemark()
    {
        if (System.currentTimeMillis() > last_check + DB_CHECK_MS)
        {  
            Connection conn = null;
            try
            {
                conn = DB.openConnection("share_db");

                PreparedStatement ps = conn.prepareStatement("select * from witty_remarks where used=false order by order_id asc limit 1");
                ResultSet rs = ps.executeQuery();

                if (rs.next())
                {

                    last_remark = rs.getString("remark");
                    int order = rs.getInt("order_id");
                    System.out.println("Witty remark selected (" + order + ") - '" + last_remark + "'");

                }
                else
                {
                    System.out.println("No more witty remarks");
                    last_remark=null;
                }

                rs.close();
                ps.close();
            
            
                last_check=System.currentTimeMillis();
            }
            catch(java.sql.SQLException e)
            {
                System.out.println("Error getting remark: " + e);    
            }
            finally
            {
                DB.safeClose(conn);
            }

        }


    }

    public void markUsed(String remark)
    {
        Connection conn = null;
        try
        {
            conn = DB.openConnection("share_db");

            PreparedStatement ps = conn.prepareStatement("update witty_remarks set used=true where remark=?");
            ps.setString(1, remark);
            ps.execute();
            ps.close();

        }
        catch(java.sql.SQLException e)
        {
            System.out.println("Failed to mark remark as no longer remarkable: " + e);
        }
        finally
        {
            DB.safeClose(conn);
        }

        synchronized(this)
        {
            last_check = 0L;
            this.notifyAll();
        }
    }

}
