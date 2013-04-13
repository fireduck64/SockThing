
package sockthing;

import java.net.DatagramSocket;

import java.net.DatagramPacket;

/**
 * Listens on a UDP port for a packet which indicates there is a new block
 * Completely optional to use.
 */
public class NotifyListenerUDP extends Thread
{
    private StratumServer server;
    private int port;

    public NotifyListenerUDP(StratumServer server)
    {
        this.server = server;
        server.getConfig().require("notify_port");

        port = server.getConfig().getInt("notify_port");

        this.setName("NotifyListenerUDP");
        this.setDaemon(true);


    }
    public void run()
    {
        try
        {
            DatagramSocket ds = new DatagramSocket(port);

            while(true)
            {
                DatagramPacket dp = new DatagramPacket(new byte[1024], 1024);

                ds.receive(dp);
                server.notifyNewBlock();
            }
        }
        catch(java.io.IOException e)
        {
            System.out.println("Unable to continue notify listen");
        }

    }


}
