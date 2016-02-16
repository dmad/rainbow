package damd.rainbow.net.pipeline.test;

import java.io.FileInputStream;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;

import damd.rainbow.behavior.Engine;

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;
import damd.rainbow.net.SocketHandlerFactory;

import damd.rainbow.net.pipeline.PipelineState;
import damd.rainbow.net.pipeline.PipelineEvent;
import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSource;
import damd.rainbow.net.pipeline.PipelineTarget;
import damd.rainbow.net.pipeline.PipelineSocketHandler;
import damd.rainbow.net.pipeline.PipelineSSLHandler;
import damd.rainbow.net.pipeline.BufferedOutbound;

import damd.rainbow.util.concurrent.DaemonThreadFactory;

public class SSLHandlerTest
    implements
	SocketHandlerFactory,
	PipelineTarget
{
    private Logger logger;

    private Pipeline pipeline;
    private PipelineSource source;

    private BufferedOutbound buffered_outbound;

    private ExecutorService es = Executors
	.newCachedThreadPool (new DaemonThreadFactory ());

    private SSLHandlerTest ()
    {
	logger = Logger.getLogger (getClass ().getName ());
	logger.info ("Logger(" + logger.getName ()
		     + ") finest(" + logger.isLoggable (Level.FINEST)
		     + ")");
    }

    // >>> SocketHandlerFactory

    public SocketHandler createSocketHandler ()
	throws Exception
    {
	final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);
	final PipelineSSLHandler sslh = new PipelineSSLHandler ();
	final KeyStore ks = KeyStore.getInstance ("JKS");
	final KeyManagerFactory kmf = KeyManagerFactory.getInstance ("SunX509");
	final char[] passphrase = "passphrase".toCharArray();

	ks.load (new FileInputStream ("/Users/dirk/testkeys"), passphrase);
	kmf.init(ks, passphrase);
	sslh.addKeyManagers (kmf.getKeyManagers ());

	new Pipeline ()
	    .add (sh)
	    //.add (new PipelineInterceptor ())
	    .add (sslh)
	    .add (new SSLHandlerTest ());

	System.out.println ("Sockethandler(" + sh + ")");

	return sh;
    }

    // <<< SocketHandlerFactory

    // >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
    }

    public void stateHasChanged (final PipelineState new_state)
    {
	// We do not need to do anything an a pipepline state change
    }

    public void openNode (final short phase)
    {
	switch (phase) {
	case 0:
	    buffered_outbound = new BufferedOutbound (source);
	    break;
	case 1:
	    buffered_outbound.write (ByteBuffer.wrap ("Hello".getBytes ()));
	    break;
	}
    }

    public void closeNode ()
    {
	buffered_outbound = null;
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
	int size = data.remaining ();

	System.out.println ("before read: position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");

	while (data.hasRemaining ()) {
	    final byte b = data.get ();

	    System.out.print (Byte.toString (b) + " ");
	}
	System.out.println ("");

	System.out.println ("after read: position(" + data.position ()
			    + ") limit(" + data.limit ()
			    + ") remaining(" + data.remaining ()
			    + ")");

	// 'consume' all data
	//data.position (data.limit ());

	buffered_outbound.write
	    (ByteBuffer.wrap
	     (("got " + size + " bytes").getBytes ()));
    }

    public void giveOutbound (final ByteBuffer outbound)
    {
	buffered_outbound.giveOutbound (outbound);
    }

    // <<< PipelineTarget

    public static void main (String[] args)
    {
	Logger.getLogger (Logger.GLOBAL_LOGGER_NAME).setLevel (Level.ALL);
	Logger.getLogger ("damd.rainbow").setLevel (Level.ALL);

	System.out.println ("logger level set to: "
			    + (Logger.getLogger (Logger.GLOBAL_LOGGER_NAME).
			       getLevel ()));
	try {
	    SocketListener listener = new SocketListener ("test");
	    listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
								10000));

	    listener.setHandlerFactory (new SSLHandlerTest ());
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
