package damd.rainbow.net.pipeline.stanza.test;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import org.w3c.dom.Document;

import org.xml.sax.SAXException;
import org.xml.sax.Attributes;

import damd.rainbow.behavior.Engine;

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;
import damd.rainbow.net.SocketHandlerFactory;

import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSocketHandler;

import damd.rainbow.net.pipeline.stanza.XmlStanzaHandler;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegator;
import damd.rainbow.net.pipeline.stanza.XmlStanzaDelegate;

public class XmlStanzaHandlerTest
    implements XmlStanzaDelegate
{
    private static class Factory
	implements SocketHandlerFactory
    {
	private ExecutorService es = Executors.newCachedThreadPool ();

	public SocketHandler createSocketHandler ()
	{
	    final PipelineSocketHandler sh = new PipelineSocketHandler (es, es);

	    new Pipeline ()
		.add (sh)
		.add (new XmlStanzaHandler (new XmlStanzaHandlerTest ()));

	    return sh;
	}
    }

    private Logger logger;

    private XmlStanzaDelegator delegator;
    private String stream_tag;

    public XmlStanzaHandlerTest ()
    {
	logger = Logger.getLogger (getClass ().getName ());
    }

    // >>> XmlStanzaDelegate

    public void setDelegator (final XmlStanzaDelegator delegator)
    {
	this.delegator = delegator;
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
	for (int i = 0;i < 1000;++i) {
	    delegator.write ("<got>");
	    delegator.write (stanza);
	    delegator.write ("</got>");
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
