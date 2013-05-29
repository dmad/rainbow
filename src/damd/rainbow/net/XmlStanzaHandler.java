package damd.rainbow.net;

import java.util.Deque;
import java.util.ArrayDeque;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.xml.sax.SAXException;
import org.xml.sax.Locator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;

import damd.rainbow.xml.XmlException;
import damd.rainbow.xml.DomBuilder;
import damd.rainbow.xml.DomReader;

import uk.org.retep.niosax.NioSaxParserFactory;
import uk.org.retep.niosax.NioSaxParser;
import uk.org.retep.niosax.NioSaxSource;

public class XmlStanzaHandler
    implements
	SocketHandler
{
    public interface Delegate
    {
	public void setDelegator (XmlStanzaHandler delegator);

	public boolean openStream (String name, Attributes attrs);
	public void closeStream ();

	public void handleStanza (Document stanza);

	public void cleanup ();
    }

    private enum StreamState
    {
	INVALID,
	CLOSED,
	VALID,
	OPEN,
	CLOSING
    }

    private Logger logger;

    private final Delegate delegate;

    private SocketListener listener; // sychronized on 'this' for assignment
    private SocketChannel channel; // synchronized on 'this' for assignment

    private Thread worker; // synchronized on 'this' for assignment

    private Deque<String> write_deque; // synchronized on itself
    private Selector channel_selector; // not synchronized

    private StreamState stream_state; /* only used by WorkerThread
					 and ContentHandlerImpl
					 in worker thread, so no sync
					 needed ! */

    public XmlStanzaHandler (Delegate delegate)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.delegate = delegate;
	delegate.setDelegator (this);

	write_deque = new ArrayDeque<String> ();
    }

    public void write (final String value)
    {
	if (null != value && !(value.isEmpty ())) {
	    synchronized (write_deque) {
		write_deque.offerLast (value);
	    }

	    synchronized (this) {
		if (null != worker)
		    channel_selector.wakeup ();
	    }
	}
    }

    public void write (final Document stanza)
    {
	if (null != stanza)
	    write (new DomReader (stanza).serialize (false));
    }

    // >>> SocketHandler

    public synchronized void open (SocketChannel channel,
				   SocketListener listener)
	throws IOException
    {
	if (null != worker)
	    throw new IllegalStateException ("Already open");

	if (channel.isBlocking ())
	    channel.configureBlocking (false);

	this.listener = listener;
	this.channel = channel;

	if (null == channel_selector || !(channel_selector.isOpen ()))
	    channel_selector = Selector.open ();

	{
	    StringBuilder name = new StringBuilder ();

	    name.append ("channel(");
	    name.append (channel.toString ());
	    name.append (")");

	    if (null != listener) {
		name.append (", listener(");
		name.append (listener.toString ());
		name.append (")");
	    }

	    logger = Logger.getLogger (getClass ().getName ()
				       + "#"
				       + name.toString ());

	    {
		WorkerThread worker_thread;

		worker_thread = new WorkerThread (this);
		worker = new Thread (worker_thread,
				     worker_thread.getClass ().getName ()
				     + "("
				     + name.toString ()
				     + ")");
	    }

	    if (!(worker.isDaemon ()))
		worker.setDaemon (true);

	    worker.start ();
	}
    }

    public synchronized void close ()
	throws IOException
    {
	if (null != worker)
	    channel_selector.close ();
    }

    // <<< SocketHandler

    private class ContentHandlerImpl
	implements ContentHandler
    {
	private StringBuilder element_characters;

	private DomBuilder stanza;

	public void startElement (String uri, String local_name,
				  String name, Attributes attrs)
	{
	    element_characters.setLength (0);

	    switch (stream_state) {
	    case VALID:
		stream_state = delegate.openStream (name, attrs)
		    ? StreamState.OPEN
		    : StreamState.INVALID;
		break;
	    case OPEN:
		if (null == stanza) {
		    try {
			stanza = new DomBuilder (name);
		    } catch (XmlException x) {
			// TODO: handleException (x);
		    }
		} else
		    stanza.addChildElement (name);

		if (null != attrs && attrs.getLength () > 0)
		    for (int a = 0;a < attrs.getLength ();++a)
			stanza.addAttribute
			    (attrs.getLocalName (a),
			     attrs.getValue (a));
		break;
	    default:
		logger.warning ("Ignoring start element("
				+ name
				+ ") as the stream state is "
				+ stream_state);
	    }
	}

	public void endElement (String uri, String local_name,
				String name)
	{
	    final String data = (0 == element_characters.length ()
				 ? null
				 : element_characters.toString ());

	    element_characters.setLength (0);


	    switch (stream_state) {
	    case OPEN:
		if (null == stanza) {
		    delegate.closeStream ();
		    stream_state = StreamState.CLOSING;
		} else {
		    if (!(stanza.getName ().equals (name))) {
			logger.warning ("Unbalanced element found");
			stream_state = StreamState.INVALID;
		    } else {
			if (null != data)
			    stanza.addText (data);

			if (stanza.hasParent ())
			    stanza.moveToParent ();
			else {
			    delegate.handleStanza (stanza.getDocument ());
			    stanza = null;
			}
		    }
		}
		break;
	    default:
		logger.warning ("Ignoring end element("
				+ name
				+ ") as the stream state is "
				+ stream_state);
	    }
	}

	public void characters (char[] ch, int start, int length)
	{
	    element_characters.append (ch, start, length);
	}

	public void startDocument ()
	{
	    element_characters = new StringBuilder ();

	    stanza = null;
	}

	public void endDocument () {}

	public void startPrefixMapping (String prefix, String uri) {}

	public void endPrefixMapping (String prefix) {}

	public void ignorableWhitespace (char[] ch, int start, int length) {}

	public void processingInstruction (String target, String data) {}

	public void setDocumentLocator (Locator locator) {}

	public void skippedEntity (String name) {}
    }

    // >>> Runnable

    private class WorkerThread
	implements Runnable
    {
	private XmlStanzaHandler parent;

	private NioSaxParser parser;
	private NioSaxSource source;

	private ByteBuffer input_buffer;
	private ByteBuffer output_buffer;
	private CharsetEncoder output_encoder;
	private CharBuffer char_buffer;

	private SelectionKey selection_key;

	public WorkerThread (XmlStanzaHandler parent)
	{
	    this.parent = parent;
	}

	private void setup ()
	    throws
		IOException,
		SAXException
	{
	    input_buffer = ByteBuffer.allocate (2048);

	    parser = NioSaxParserFactory.getInstance ().newInstance ();
	    parser.setHandler (new ContentHandlerImpl ());
	    source = new NioSaxSource (input_buffer);

	    char_buffer = CharBuffer.allocate (2048);
	    output_buffer = ByteBuffer.allocate (2048);
	    output_encoder = Charset.forName ("UTF-8").newEncoder ();

	    selection_key = channel.register (channel_selector,
					      SelectionKey.OP_READ);

	    stream_state = StreamState.VALID;

	    parser.startDocument ();
	}

	private void cleanup ()
	{
	    if (null != parser) {
		try {
		    parser.endDocument ();
		} catch (SAXException x) {
		    logger.log (Level.WARNING,
				"While invoking endDocument",
				x);
		}
	    }

	    delegate.cleanup ();

	    output_encoder = null;

	    input_buffer = null;
	    output_buffer = null;
	    char_buffer = null;

	    if (null != channel_selector) {
		try {
		    channel_selector.close ();
		} catch (IOException x) {
		    logger.log (Level.WARNING,
				"While closing channel_selector",
				x);
		}
	    }

	    synchronized (parent) {
		if (null != listener) {
		    listener.removeHandler (parent);
		    listener = null;
		}

		if (null != channel) {
		    try {
			channel.close ();
		    } catch (IOException x) {
			logger.log (Level.WARNING,
				    "While closing channel",
				    x);
		    } finally {
			channel = null;
		    }
		}

		worker = null;
	    }
	}

	private void select ()
	    throws
		IOException,
		SAXException
	{
	    synchronized (write_deque) {
		if (char_buffer.remaining () > 0
		    && write_deque.size () > 0) {
		    String to_buf = write_deque.pollFirst ();
		    String to_deq = null;

		    if (to_buf.length () > char_buffer.remaining ()) {
			String orig = to_buf;

			to_buf = orig.substring (0, char_buffer.remaining ());
			to_deq = orig.substring (char_buffer.remaining ());
		    }

		    char_buffer.append (to_buf);

		    if (null != to_deq)
			write_deque.offerFirst (to_deq);
		}
	    }

	    if (char_buffer.position () > 0
		&& output_buffer.remaining () > 0) {
		char_buffer.flip ();
		output_encoder.encode (char_buffer, output_buffer, false);
		char_buffer.compact ();
	    }

	    selection_key.interestOps
		(0 == output_buffer.position ()
		 ? selection_key.interestOps () & ~(SelectionKey.OP_WRITE)
		 : selection_key.interestOps () | SelectionKey.OP_WRITE);

	    if (StreamState.CLOSING == stream_state)
		if (output_buffer.position () > 0)
		    selection_key.interestOps
			(selection_key.interestOps ()
			 & ~(SelectionKey.OP_READ));
		else
		    stream_state = StreamState.CLOSED;

	    if (stream_state.ordinal () >= StreamState.VALID.ordinal ()) {
		if (channel_selector.select () > 0) {
		    if (0 != (selection_key.readyOps ()
			      & SelectionKey.OP_READ)) {
			int read = channel.read (input_buffer);

			if (read < 0) // end of stream
			    stream_state = StreamState.CLOSED;
			else if (read > 0) {
			    input_buffer.flip ();
			    try {
				parser.parse (source);
			    } finally {
				source.compact ();
			    }
			}
		    }

		    if (0 != (selection_key.readyOps ()
			      & SelectionKey.OP_WRITE)
			&& output_buffer.position () > 0) {
			output_buffer.flip ();
			try {
			    channel.write (output_buffer);
			} finally {
			    output_buffer.compact ();
			}
		    }

		    channel_selector.selectedKeys ().clear ();
		}
	    }
	}

	public void run ()
	{
	    logger.fine ("SocketHandler worker thread has started");

	    try {
		setup ();

		while (stream_state.ordinal ()
		       >= StreamState.VALID.ordinal ())
		    select ();
	    } catch (ClosedSelectorException x) {
		// swallow, requested to stop
	    } catch (IOException x) {
		logger.log (Level.WARNING, "While interacting with channel", x);
	    } catch (SAXException x) {
		logger.log (Level.WARNING, "While parsing stream", x);
	    } finally {
		cleanup ();
	    }

	    logger.fine ("SocketHandler worker thread has stopped");
	}
    }
}
