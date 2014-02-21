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
	ByteSocketHandler.Delegate
{
    private ByteSocketHandler delegator;

    public SocketHandler createSocketHandler ()
    {
	ExecutorService es = Executors.newCachedThreadPool ();

	return new ByteSocketHandler (es, es, this);
    }

    public void setDelegator (final ByteSocketHandler delegator)
    {
	this.delegator = delegator;
    }

    public void handleInput (ByteBuffer data)
    {
	long start = System.currentTimeMillis ();

	System.out.println ("position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");

	try {
	    while (System.currentTimeMillis () - start < 10000) {
		delegator.write (data);
		Thread.sleep (1000);
	    }
	} catch (InterruptedException x) {
	}
    }

    public void cleanup ()
    {
    }

    public static void main (String[] args)
    {
	try {
	    ByteSocketHandlerTest that = new ByteSocketHandlerTest ();
	    SocketListener listener = new SocketListener ("test");
	    listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
								10000));
	    listener.setHandlerFactory (that);
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
