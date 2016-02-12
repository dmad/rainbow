package damd.rainbow.net.pipeline.stanza.test;

import java.io.FileInputStream;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import java.nio.channels.SocketChannel;

import java.security.KeyStore;

import javax.net.ssl.TrustManagerFactory;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

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

public class XmlStanzaHandlerClientTest
    implements XmlStanzaDelegate
{
    private XmlStanzaDelegator delegator;
    private String stream_tag;

    // >>> XmlStanzaDelegate

    public void setDelegator (final XmlStanzaDelegator delegator)
    {
	this.delegator = delegator;
    }

    public void streamIsReadyForWriting ()
    {
	System.out.println ("writing to server");
	delegator.write ("<hello><world/></hello>");
	delegator.flush ();
    }

    public boolean openStream (final String name, final Attributes attrs)
    {
	stream_tag = name;
	System.out.println ("<" + stream_tag + ">");

	return true;
    }

    public void closeStream ()
    {
	System.out.println ("</" + stream_tag + ">");
    }

    public void handleStanza (final Document stanza)
    {
	System.out.println (new DomReader (stanza).serialize ());
    }

    public void cleanup ()
    {
    }

    // <<< XmlStanzaDelegate

    public void run (final String[] args)
	throws Exception
    {
	final SocketChannel channel;
	final ExecutorService es = Executors.newCachedThreadPool ();
	final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);
	final PipelineSSLHandler sslh = new PipelineSSLHandler ();
	final KeyStore ks = KeyStore.getInstance ("JKS");
	final TrustManagerFactory tmf = TrustManagerFactory
	    .getInstance ("SunX509");
	final char[] passphrase = "passphrase".toCharArray();

	ks.load (new FileInputStream ("/Users/dirk/testkeys"), passphrase);
	tmf.init(ks);
	sslh.addTrustManagers (tmf.getTrustManagers ());

	channel = SocketChannel.open
	    (new InetSocketAddress ("localhost", 10000));

	new Pipeline ()
	    .add (sh)
	    .add (sslh)
	    .add (new XmlStanzaHandler (this));

	sh.open (channel, null);
    }

    public static void main (final String[] args)
    {
	try {
	    new XmlStanzaHandlerClientTest ().run (args);
	} catch (Throwable t) {
	    t.printStackTrace ();
	}
    }
}
