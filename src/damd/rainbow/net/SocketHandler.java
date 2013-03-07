package damd.rainbow.net;

import java.io.IOException;

import java.nio.channels.SocketChannel;

public interface SocketHandler
{
    public void open (SocketChannel channel, SocketListener listener)
	throws IOException;
    public void close ()
	throws IOException;
}
