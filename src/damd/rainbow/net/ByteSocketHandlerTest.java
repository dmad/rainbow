package damd.rainbow.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import damd.rainbow.behavior.Engine;

public class ByteSocketHandlerTest
    implements
	SocketHandlerFactory,
	BytePipelineTarget
{
    private BytePipelineSource source;

    // >>> SocketHandlerFactory

    public SocketHandler createSocketHandler ()
    {
	ExecutorService es = Executors.newCachedThreadPool ();

	return new ByteSocketHandler (es, es, this);
    }

    // <<< SocketHandlerFactory

    // >>> BytePipelineTarget

    public void setSource (final BytePipelineSource source)
    {
	this.source = source;
    }

    public void handleInput (ByteBuffer data)
    {
	long start = System.currentTimeMillis ();

	System.out.println ("before read: position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");

	for (int i = 0;i < 10 && data.hasRemaining ();++i) {
	    final byte b = data.get ();

	    System.out.print (Byte.toString (b) + " ");
	}
	System.out.println ("");

	System.out.println ("after read: position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");

	/*
	data.compact ();

	System.out.println ("after compact: position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");
	*/

	source.write (ByteBuffer.wrap ("got it".getBytes ()));
    }

    public void cleanup ()
    {
    }

    // <<< BytePipelineTarget

    public static void main (String[] args)
    {
	try {
	    SocketListener listener = new SocketListener ("test");
	    listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
								10000));

	    listener.setHandlerFactory (new ByteSocketHandlerTest ());
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
