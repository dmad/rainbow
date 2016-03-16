package damd.rainbow.net.pipeline.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.atomic.AtomicLong;

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

import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineState;
import damd.rainbow.net.pipeline.PipelineSource;
import damd.rainbow.net.pipeline.PipelineTarget;
import damd.rainbow.net.pipeline.PipelineSocketHandler;
import damd.rainbow.net.pipeline.PipelineSSLHandler;
import damd.rainbow.net.pipeline.BufferedOutbound;

import damd.rainbow.util.concurrent.DaemonThreadFactory;

public class SpeedTest
    implements
	SocketHandlerFactory
{
    private Logger server_logger;
    private Logger client_logger;

    private AtomicLong received_bytes = new AtomicLong ();
    private AtomicLong buffered_bytes = new AtomicLong ();
    private AtomicLong transmitted_bytes = new AtomicLong ();

    private ExecutorService es = Executors
	.newCachedThreadPool (new DaemonThreadFactory ());

    private class Server
	implements
	    PipelineTarget
    {
	private Pipeline pipeline;
	private PipelineSource source;
	private BufferedOutbound buffered_outbound;

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
		buffered_outbound = new BufferedOutbound (this.source);
		break;
	    case 1:
		// nothing to do, we do not initiate the conversation
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

	public void handleInbound (final ByteBuffer in)
	{
	    if (in.hasRemaining ()) {
		final ByteBuffer out = ByteBuffer.allocate (in.remaining ());

		received_bytes.addAndGet (in.remaining ());

		out.put (in); // echo back to client
		out.flip ();

		{
		    final int remaining = buffered_outbound.remaining ();

		    buffered_outbound.write (out);
		    buffered_bytes.addAndGet (buffered_outbound.remaining ()
					      - remaining);
		}
	    }
	}

	public void giveOutbound (final ByteBuffer outbound)
	{
	    final int remaining = buffered_outbound.remaining ();

	    buffered_outbound.giveOutbound (outbound);

	    transmitted_bytes.addAndGet (remaining
					 - buffered_outbound.remaining ());
	}

	// <<< PipelineTarget
    }

    private SpeedTest ()
    {
	server_logger = Logger.getLogger ("server");
	client_logger = Logger.getLogger ("client");
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

	ks.load (new java.io.FileInputStream ("/Users/dirk/testkeys"), passphrase);
	kmf.init(ks, passphrase);
	sslh.addKeyManagers (kmf.getKeyManagers ());

	new Pipeline ()
	    .add (sh)
	    .add (sslh)
	    .add (new Server ());

	return sh;
    }

    // <<< SocketHandlerFactory

    public void main2 (final String[] args)
	throws Exception
    {
	final SocketListener listener = new SocketListener ("echo");

	listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
							    10000));
	listener.setHandlerFactory (this);
	listener.changeState (Engine.State.RUNNING);

	{
	    long prev_rx = 0L, prev_buf = 0L, prev_tx = 0L;

	    while (true) {
		final long rx, buf, tx;

		Thread.sleep (1000);
		rx = received_bytes.get ();
		buf = buffered_bytes.get ();
		tx = transmitted_bytes.get ();
		if (rx != prev_rx) {
		    System.out.println ("RX:" + rx + " delta("+ (rx - prev_rx) +")");
		    prev_rx = rx;
		}
		if (buf != prev_buf) {
		    System.out.println ("BF:" + buf + " delta(" + (buf - prev_buf) + ")");
		    prev_buf = buf;
		}
		if (tx != prev_tx) {
		    System.out.println ("TX:" + tx + " delta (" + (tx - prev_tx) + ")");
		    prev_tx = tx;
		}
	    }
	}
    }

    public static void main (final String[] args)
    {
	try {
	    new SpeedTest ().main2 (args);
	} catch (final Throwable t) {
	    t.printStackTrace ();
	}
    }

}
