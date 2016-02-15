package damd.rainbow.net.pipeline.stanza.test;

import java.io.FileInputStream;

import java.util.Arrays;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

import java.security.KeyStore;

import javax.net.ssl.TrustManagerFactory;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import damd.rainbow.logging.DevLogFormatter;

import damd.rainbow.xml.DomReader;

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

public class XmlStanzaHandlerClientTest
    implements XmlStanzaDelegate
{
    private XmlStanzaDelegator delegator;

    private String[] arguments;
    private String stream_tag;

    // >>> XmlStanzaDelegate

    public void setDelegator (final XmlStanzaDelegator delegator)
    {
	this.delegator = delegator;
    }

    public void streamIsReadyForWriting ()
    {
	System.out.print ("c2s> ");
	for (final String arg : arguments) {
	    System.out.println (arg);
	    delegator.write (arg);
	}
	System.out.println ("");
	delegator.flush ();
    }

    public boolean openStream (final String name, final Attributes attrs)
    {
	stream_tag = name;
	System.out.println ("s2c> openStream(" + stream_tag + ")");

	return true;
    }

    public void closeStream ()
    {
	System.out.println ("s2c> closeStream(" + stream_tag + ")");
    }

    public void handleStanza (final Document stanza)
    {
	System.out.println ("s2c> " + new DomReader (stanza).serialize ());
    }

    public void cleanup ()
    {
    }

    // <<< XmlStanzaDelegate

    public void run (final String[] args)
	throws Exception
    {
	final Pipeline pipeline = new Pipeline ();
	final ExecutorService es = Executors.newCachedThreadPool ();
	final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);
	final SocketChannel channel;
	final boolean ssl;

	ssl = (args.length >= 1 && "--ssl".equals (args[0]));
	if (ssl)
	    this.arguments = Arrays.copyOfRange (args, 1, args.length);
	else
	    this.arguments = args;

	pipeline
	    .add (sh)
	    .add (new PipelineInterceptor ("raw"));

	if (ssl) {
	    final PipelineSSLHandler sslh = new PipelineSSLHandler ();
	    final KeyStore ks = KeyStore.getInstance ("JKS");
	    final TrustManagerFactory tmf = TrustManagerFactory
		.getInstance ("SunX509");
	    final char[] passphrase = "passphrase".toCharArray();

	    ks.load (new FileInputStream ("/Users/dirk/testkeys"), passphrase);
	    tmf.init(ks);
	    sslh.setClientMode (true);
	    sslh.addTrustManagers (tmf.getTrustManagers ());

	    pipeline.add (sslh);
	}

	pipeline.add (new PipelineInterceptor ("xml"));
	pipeline.add (new XmlStanzaHandler (this));

	channel = SocketChannel.open
	    (new InetSocketAddress ("localhost", 10000));

	sh.open (channel, null);
    }

    public static void main (final String[] args)
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
	    new XmlStanzaHandlerClientTest ().run (args);
	} catch (Throwable t) {
	    t.printStackTrace ();
	}
    }
}
