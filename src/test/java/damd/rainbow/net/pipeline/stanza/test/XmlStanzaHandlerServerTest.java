package damd.rainbow.net.pipeline.stanza.test;

import java.io.FileInputStream;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import damd.rainbow.behavior.Engine;

import damd.rainbow.logging.DevLogFormatter;

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;
import damd.rainbow.net.SocketHandlerFactory;

import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSocketHandler;
import damd.rainbow.net.pipeline.PipelineSSLHandler;

import damd.rainbow.net.pipeline.stanza.XmlStanzaHandler;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegator;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegate;

import damd.rainbow.net.pipeline.test.PipelineInterceptor;

public class XmlStanzaHandlerServerTest
    implements XmlStanzaDelegate
{
    private static class Factory
	implements SocketHandlerFactory
    {
	private boolean ssl;
	private ExecutorService es = Executors.newCachedThreadPool ();

	public Factory (final boolean ssl)
	{
	    this.ssl = ssl;
	}

	public SocketHandler createSocketHandler ()
	    throws Exception
	{
	    final Pipeline pipeline = new Pipeline ();
	    final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);

	    pipeline.add (sh);

	    if (ssl) {
		final PipelineSSLHandler sslh = new PipelineSSLHandler ();
		final KeyStore ks = KeyStore.getInstance ("JKS");
		final KeyManagerFactory kmf = KeyManagerFactory
		    .getInstance ("SunX509");
		final char[] passphrase = "passphrase".toCharArray();

		ks.load (new FileInputStream ("/Users/dirk/testkeys"),
			 passphrase);
		kmf.init(ks, passphrase);
		sslh.addKeyManagers (kmf.getKeyManagers ());

		pipeline.add (sslh);
	    }

	    pipeline.add (new XmlStanzaHandler
			  (new XmlStanzaHandlerServerTest ()));

	    return sh;
	}
    }

    private Logger logger;

    private XmlStanzaDelegator delegator;
    private String stream_tag;

    public XmlStanzaHandlerServerTest ()
    {
	logger = Logger.getLogger (getClass ().getName ());
    }

    // >>> XmlStanzaDelegate

    public void setDelegator (final XmlStanzaDelegator delegator)
    {
	this.delegator = delegator;
    }

    public void streamIsReadyForWriting ()
    {
	// thanks for notifying us, but we do not need it
    }

    public boolean openStream (final String name, final Attributes attrs)
    {
	logger.finest ("openStream called");

	stream_tag = name;
	delegator.write ("<" + stream_tag + ">");

	return true;
    }

    public void closeStream ()
    {
	delegator.write ("</" + stream_tag + ">");
    }

    public void handleStanza (final Document stanza)
    {
	delegator.write ("<got>");
	delegator.write (stanza);
	delegator.write ("</got>");
	delegator.flush ();
    }

    public void cleanup ()
    {
    }

    // <<< XmlStanzaDelegate

    public static void main (String[] args)
    {
	{
	    final Logger logger = Logger.getLogger ("");
	    final Handler handler = new ConsoleHandler ();

	    // Get rid of all handlers of the root logger
	    for (final Handler h : logger.getHandlers ())
		logger.removeHandler (h);

	    handler.setFormatter (new DevLogFormatter ());
	    handler.setLevel (Level.ALL); // default is INFO
	    logger.addHandler (handler);

	    logger.setLevel (Level.ALL);
	}

	try {
	    final boolean ssl;
	    final SocketListener listener = new SocketListener ("test");

	    ssl = (1 == args.length && "--ssl".equals (args[0]));

	    listener.setListenerAddress (new InetSocketAddress
					 ((InetAddress) null, 10000));

	    listener.setHandlerFactory (new Factory (ssl));
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
