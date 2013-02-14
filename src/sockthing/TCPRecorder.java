package sockthing;
import java.net.Socket;
import java.net.ServerSocket;

import java.io.InputStream;
import java.io.OutputStream;

import java.io.FileOutputStream;
import java.io.PrintStream;

import java.util.UUID;

public class TCPRecorder
{
    public static void main(String args[]) throws Exception
    {
        String dir = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        int local_port = Integer.parseInt(args[3]);

        new TCPRecorder(dir, host, port, local_port);
    }

    public TCPRecorder(String log_dir, String remote_host, int remote_port, int local_port)
        throws Exception
    {
        ServerSocket ss = new ServerSocket(local_port);
        ss.setReuseAddress(true);

        while(ss.isBound())
        {
            try
            {
                Socket s = ss.accept();

                Socket remote_s = new Socket(remote_host, remote_port);
                ConnectionContext ctx = new ConnectionContext(log_dir, s, remote_s);


            }
            catch(Throwable t)
            {
                t.printStackTrace();
            }


        }
        
    }

    public class ConnectionContext
    {

        public Socket local_sock;
        public Socket remote_sock;
        public ReaderThread local_reader;
        public ReaderThread remote_reader;
        public volatile boolean open;

        private PrintStream log_out;

        public ConnectionContext(String log_dir, Socket local, Socket remote)
            throws Exception
        {
            this.local_sock = local;
            this.remote_sock = remote;

            String log_file = log_dir +"/" + UUID.randomUUID().toString() + ".txt";
            System.out.println("Starting connection: " + log_file);

            log_out = new PrintStream(new FileOutputStream(log_file));

            open=true;

            local_reader = new ReaderThread(this, local_sock, remote_sock, "local");
            local_reader.start();
            remote_reader = new ReaderThread(this, remote_sock, local_sock, "remote");
            remote_reader.start();
        }

        public void close()
        {
            open=false;
            try
            {
                local_sock.close();
            }
            catch(Throwable t){}

            try
            {
                remote_sock.close();
            }
            catch(Throwable t){}

            try
            {
                log_out.flush();
                log_out.close();
            }
            catch(Throwable t){}
        }

        public void log(byte[] buff, int offset, int size, String side)
        {
            String str = new String(buff, offset, size);
            synchronized(log_out)
            {
                log_out.println("----------------------------");
                log_out.println("Side: " + side);
                log_out.println(str);

            }

        }

    }

    public class ReaderThread extends Thread
    {
        ConnectionContext ctx;
        Socket read_socket;
        Socket write_socket;
        String side;

        public ReaderThread(ConnectionContext ctx, Socket read_socket, Socket write_socket, String side)
        {
            this.ctx = ctx;
            this.read_socket = read_socket;
            this.write_socket = write_socket;
            this.side = side;
        }

        public void run()
        {
            try
            {
                InputStream in = read_socket.getInputStream();
                OutputStream out = write_socket.getOutputStream();
                byte[] buff=new byte[65536];
                while(ctx.open)
                {
                    int sz = in.read(buff);
                    if (sz < 0)
                    {
                        ctx.close();
                        return;
                    }

                    ctx.log(buff, 0, sz, side);

                    out.write(buff, 0, sz);

                }

            }
            catch(Throwable t)
            {
                ctx.close();
            }


        }

    }

}
