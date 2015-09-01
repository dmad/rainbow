package damd.rainbow.net;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

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
	BytePipelineTarget
{
    public interface Delegate
    {
	public void setDelegator (XmlStanzaHandler delegator);

	public boolean openStream (String name, Attributes attrs);
	public void closeStream ();

	public void handleStanza (Document stanza);

	public void cleanup ();
    }

    private Logger logger;

    private String id;

    private final Delegate delegate;

    private BytePipelineSource source;

    private NioSaxParser parser;

    public XmlStanzaHandler (final Delegate delegate)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.delegate = delegate;
    }

    public String toString ()
    {
	return getClass ().getName () + "(id(" + id + "))";
    }

    // >>> BytePipelineTarget

    public void setSource (final BytePipelineSource source)
    {
	logger.info ("setSource called");
	this.source = source;
	this.delegate.setDelegator (this);
    }

    public void handleInput (final ByteBuffer input)
	throws SAXException
    {
	if (1 == 1) {
	    logger.info ("hello");
	    return;
	}
	try {
	    if (null == parser) {
		logger.info ("creating parser");
		parser = NioSaxParserFactory.getInstance ().newInstance ();
		logger.info ("after parser newInstance");
		parser.setHandler (new ContentHandlerImpl ());
		logger.info ("after setHandler");
		parser.startDocument ();
		logger.info ("after startDocument");
	    }

	    parser.parse (new NioSaxSource (input));

	    logger.info ("after parse: position(" + input.position ()
			 + ") limit(" + input.limit ()
			 + ") remaining(" + input.remaining ()
			 + ")");
	} catch (Exception x) {
	    x.printStackTrace ();
	}
    }

    public void cleanup ()
    {
	if (null != parser) {
	    try {
		parser.endDocument ();
	    } catch (SAXException x) {
		logger.log (Level.WARNING, "While invoking endDocument", x);
	    } finally {
		parser = null;
	    }
	}

	delegate.cleanup ();
    }

    // <<< BytePipelineTarget

    public void write (final String value)
    {
	assert (null != source);

	if (null != value && !(value.isEmpty ()))
	    source.write
		(ByteBuffer.wrap (value.getBytes (StandardCharsets.UTF_8)));
    }

    public void write (final DomReader stanza)
    {
	if (null != stanza)
	    write (stanza.serialize ());
    }

    public void write (final Document stanza)
    {
	if (null != stanza)
	    write (new DomReader (stanza));
    }

    private class ContentHandlerImpl
	implements ContentHandler
    {
	private StringBuilder element_characters;

	private DomBuilder stanza;

	public void startElement (final String uri,
				  final String local_name,
				  final String name,
				  final Attributes attrs)
	{
	    final ConnectionState state = source.getState ();

	    element_characters.setLength (0);

	    switch (state) {
	    case VALID:
		source.setState (delegate.openStream (name, attrs)
				 ? ConnectionState.OPEN
				 : ConnectionState.INVALID);
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
				+ state);
	    }
	}

	public void endElement (final String uri,
				final String local_name,
				final String name)
	{
	    final ConnectionState state = source.getState ();
	    final String data = (0 == element_characters.length ()
				 ? null
				 : element_characters.toString ());

	    element_characters.setLength (0);

	    switch (state) {
	    case OPEN:
		if (null == stanza) {
		    delegate.closeStream ();
		    source.setState (ConnectionState.CLOSING);
		} else {
		    if (!(stanza.getName ().equals (name))) {
			logger.warning ("Unbalanced element found");
			source.setState (ConnectionState.INVALID);
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
				+ state);
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
}
