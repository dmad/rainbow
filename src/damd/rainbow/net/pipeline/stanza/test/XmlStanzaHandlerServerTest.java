package damd.rainbow.net.pipeline.stanza.test;

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

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import damd.rainbow.behavior.Engine;

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;
import damd.rainbow.net.SocketHandlerFactory;

import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSocketHandler;
import damd.rainbow.net.pipeline.PipelineSSLHandler;

import damd.rainbow.net.pipeline.stanza.XmlStanzaHandler;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegator;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegate;

public class XmlStanzaHandlerServerTest
    implements XmlStanzaDelegate
{
    private static class Factory
	implements SocketHandlerFactory
    {
	private ExecutorService es = Executors.newCachedThreadPool ();

	public SocketHandler createSocketHandler ()
	    throws Exception
	{
	    final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);
	    final PipelineSSLHandler sslh = new PipelineSSLHandler ();
	    final KeyStore ks = KeyStore.getInstance ("JKS");
	    final KeyManagerFactory kmf = KeyManagerFactory
		.getInstance ("SunX509");
	    final char[] passphrase = "passphrase".toCharArray();

	    ks.load (new FileInputStream ("/Users/dirk/testkeys"), passphrase);
	    kmf.init(ks, passphrase);
	    sslh.addKeyManagers (kmf.getKeyManagers ());

	    new Pipeline ()
		.add (sh)
		.add (sslh)
		.add (new XmlStanzaHandler (new XmlStanzaHandlerServerTest ()));

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
	for (int i = 0;i < 500000;++i) {
	    delegator.write ("<got>");
	    delegator.write (stanza);
	    delegator.write ("</got>");
	    delegator.write ("<a>lkasjf;slfjlsjflawuerpoiqoruweqirpouweoquopurpoquwporuqipowurqwruioquwe"
			     + "upqweiruopquwroeieuqwoprupqwourpowuroiqweuiporuwpoqrupwourpowurpowueporu"
			     + "</a>");
	    //if (0 == i % 10000)
	    //	delegator.flush ();
	}
	delegator.flush ();
    }

    public void cleanup ()
    {
    }

    // <<< XmlStanzaDelegate

    public static void main (String[] args)
    {
	Logger.getGlobal ().setLevel (Level.ALL);

	try {
	    SocketListener listener = new SocketListener ("test");
	    listener.setListenerAddress (new InetSocketAddress ((InetAddress) null,
								10000));

	    listener.setHandlerFactory (new Factory ());
	    listener.changeState (Engine.State.RUNNING);
	    while (true) {
		Thread.sleep (10000);
	    }
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }
}
