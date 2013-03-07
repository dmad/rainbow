package damd.rainbow.util.xml;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import java.nio.ByteBuffer;

import org.xml.sax.Locator;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import org.xml.sax.helpers.XMLReaderFactory;

public class SAXParser
{
    public interface Handler
    {
	public void handleException (Exception x);
	public void handleStartElement (String name, Attributes attrs);
	public void handleEndElement (String name, String data);
    }

    private Handler handler;

    private PipedInputStream piped_input;
    private PipedOutputStream piped_output;
    private XMLReader xml_reader;
    private StringBuilder element_data;

    private class ParserThread
	implements Runnable
    {
	public void run ()
	{
	    System.out.println ("SAXParser.ParserThread has started");

	    try {
		xml_reader.parse ((new InputSource (piped_input)));
	    } catch (IOException x) {
		handler.handleException (x);
	    } catch (SAXException x) {
		handler.handleException (x);
	    }

	    System.out.println ("SAXParser.ParserThread has stopped");
	}
    }

    private class ContentHandlerImpl
	implements ContentHandler
    {
	public void startElement (String uri,
				  String local_name,
				  String qname,
				  Attributes attrs)
	{
	    handler.handleStartElement (local_name, attrs);
	    element_data.setLength (0);
	}

	public void characters (char[] ch,
				int start,
				int length)
	{
	    element_data.append (ch, start, length);
	}

	public void endElement (String uri,
				String local_name,
				String qname)
	{
	    handler.handleEndElement (local_name,
				      (0 == element_data.length ()
				       ? null
				       : element_data.toString ()));
	    element_data.setLength (0);
	}

	public void startDocument () {}

	public void endDocument () {}

	public void startPrefixMapping (String prefix, String uri) {}

	public void endPrefixMapping (String prefix) {}

	public void ignorableWhitespace (char[] ch, int start, int length) {}

	public void processingInstruction (String target, String data) {}

	public void setDocumentLocator (Locator locator) {}

	public void skippedEntity (String name) {}
    }

    private class ErrorHandlerImpl
	implements ErrorHandler
    {
	public void error (SAXParseException x)
	{
	    handler.handleException (x);
	}

	public void fatalError (SAXParseException x)
	{
	    handler.handleException (x);
	}

	public void warning (SAXParseException x)
	{
	    handler.handleException (x);
	}
    }

    public SAXParser (Handler handler)
    {
	if (null == handler)
	    throw new NullPointerException ("handler");

	this.handler = handler;
    }

    public void start ()
    {
	try {
	    Thread thread;

	    piped_output = new PipedOutputStream ();
	    piped_input = new PipedInputStream (piped_output);
	    xml_reader = XMLReaderFactory.createXMLReader ();
	    xml_reader.setContentHandler (new ContentHandlerImpl ());
	    xml_reader.setErrorHandler (new ErrorHandlerImpl ());
	    element_data = new StringBuilder ();

	    thread = new Thread (new ParserThread (),
				 ParserThread.class.getSimpleName ());
	    if (!(thread.isDaemon ()))
		thread.setDaemon (true);
	    thread.start ();
	} catch (Exception x) {
	    handler.handleException (x);
	}
    }

    public void stop ()
    {
	System.out.println ("SAXParser.stop called");

	/* We simply close the output stream and the input stream used by
	 * the thread will EOF, which will stop the parser and the thread
	 * as a result. */
	if (null != piped_output) {
	    try {
		System.out.println ("piped_output.close () called");
		piped_output.close ();
	    } catch (IOException x) {
		handler.handleException (x);
	    } finally {
		piped_output = null;
	    }
	}
    }

    public void parse (ByteBuffer buffer)
    {
	if (null != buffer && buffer.hasRemaining ()) {
	    try {
		if (buffer.hasArray ()) {
		    piped_output.write (buffer.array (),
					buffer.position (),
					buffer.remaining ());
		    buffer.position (buffer.limit ());
		} else { // slow
		    while (buffer.hasRemaining ())
			piped_output.write (buffer.get ());
		}
		piped_output.flush ();
	    } catch (IOException x) {
		handler.handleException (x);
	    }
	}
    }
}
