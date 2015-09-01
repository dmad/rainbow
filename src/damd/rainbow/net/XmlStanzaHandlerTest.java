package damd.rainbow.net;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import java.util.logging.Logger;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;

import org.w3c.dom.Document;

import org.xml.sax.Attributes;

import damd.rainbow.behavior.Engine;

public class XmlStanzaHandlerTest
    implements XmlStanzaHandler.Delegate
{
    private static class Factory
	implements SocketHandlerFactory
    {
	private ExecutorService es = Executors.newCachedThreadPool ();

	public SocketHandler createSocketHandler ()
	{
	    return new ByteSocketHandler
		(es, es,
		 new XmlStanzaHandler (new XmlStanzaHandlerTest ()));
	}
    }

    private Logger logger;

    private XmlStanzaHandler delegator;
    private String stream_tag;

    public XmlStanzaHandlerTest ()
    {
	logger = Logger.getLogger (getClass ().getName ());
    }

    // >>> XmlStanzaHandler.Delegate

    public void setDelegator (final XmlStanzaHandler delegator)
    {
	logger.info ("setDelegator called");

	this.delegator = delegator;
    }

    public boolean openStream (final String name, final Attributes attrs)
    {
	logger.info ("openStream called");

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
    }

    public void cleanup ()
    {
    }

    // <<< XmlStanzaHandler.Delegate

    public static void main (String[] args)
    {
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
