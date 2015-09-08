package damd.rainbow.net.pipeline.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import damd.rainbow.behavior.Engine;

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;
import damd.rainbow.net.SocketHandlerFactory;

import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSource;
import damd.rainbow.net.pipeline.PipelineTarget;
import damd.rainbow.net.pipeline.PipelineSocketHandler;

public class SocketHandlerTest
    implements
	SocketHandlerFactory,
	PipelineTarget
{
    private Pipeline pipeline;
    private PipelineSource source;

    // >>> SocketHandlerFactory

    public SocketHandler createSocketHandler ()
    {
	ExecutorService es = Executors.newCachedThreadPool ();
	PipelineSocketHandler sh = new PipelineSocketHandler (es, es);

	new Pipeline ().add (sh).add (new SocketHandlerTest ());

	return sh;
    }

    // <<< SocketHandlerFactory

    // >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
    }

    public void openNode (final short phase)
    {
    }

    public void closeNode ()
    {
    }

    // <<< PipelineNode

    // >>> PipelineTarget

    public void setSource (final PipelineSource source)
    {
	this.source = source;
    }

    public void handleInbound (final ByteBuffer data)
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

	source.writeOutbound (ByteBuffer.wrap ("got it".getBytes ()));
    }

    // <<< PipelineTarget

    public static void main (String[] args)
    {
	try {
	    SocketListener listener = new SocketListener ("test");
	    listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
								10000));

	    listener.setHandlerFactory (new SocketHandlerTest ());
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
