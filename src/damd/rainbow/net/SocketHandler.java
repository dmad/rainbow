package damd.rainbow.net;

import java.io.IOException;

import java.nio.channels.SocketChannel;

public interface SocketHandler
{
    public void open (SocketListener listener, SocketChannel channel)
	throws IOException;
    public void close ()
	throws IOException;
}
