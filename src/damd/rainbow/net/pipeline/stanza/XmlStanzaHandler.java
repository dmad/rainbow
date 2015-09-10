package damd.rainbow.net.pipeline.stanza;

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

import uk.org.retep.niosax.NioSaxParserFactory;
import uk.org.retep.niosax.NioSaxParser;
import uk.org.retep.niosax.NioSaxSource;

import damd.rainbow.xml.XmlException;
import damd.rainbow.xml.DomBuilder;
import damd.rainbow.xml.DomReader;

import damd.rainbow.net.pipeline.PipelineState;
import damd.rainbow.net.pipeline.PipelineEvent;
import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSource;
import damd.rainbow.net.pipeline.PipelineTarget;
import damd.rainbow.net.pipeline.BufferedOutbound;

public class XmlStanzaHandler
    implements
	PipelineTarget,
	XmlStanzaDelegator,
	ContentHandler
{
    private Logger logger;

    private final XmlStanzaDelegate delegate;

    private Pipeline pipeline;
    private PipelineSource source;

    private BufferedOutbound buffered_outbound;

    private NioSaxParser parser;

    private StringBuilder element_characters;
    private DomBuilder stanza;

    public XmlStanzaHandler (final XmlStanzaDelegate delegate)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.delegate = delegate;
	delegate.setDelegator (this);
    }

    // >>> PipelineTarget

    public void setSource (final PipelineSource source)
    {
	this.source = source;
    }

    public void handleInbound (final ByteBuffer input)
	throws SAXException
    {
	parser.parse (new NioSaxSource (input));
    }

    public void giveOutbound (final ByteBuffer outbound)
    {
	buffered_outbound.giveOutbound (outbound);
    }

    // <<< PipelineTarget

    // >>> PipelineTarget >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
    }

    public void openNode (final short phase)
	throws SAXException
    {
	switch (phase) {
	case 0:
	    buffered_outbound = new BufferedOutbound (source);
	    parser = NioSaxParserFactory.getInstance ().newInstance ();
	    parser.setHandler (this);
	    parser.startDocument ();
	    break;
	}
    }

    public void closeNode ()
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

	buffered_outbound = null;

	delegate.cleanup ();
    }

    // <<< PipelineTarget <<< PipelineNode

    // >>> XmlStanzaDelegator

    public void write (final String value)
    {
	assert (null != source);

	if (null != value && !(value.isEmpty ()))
	    buffered_outbound.write
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

    // <<< XmlStanzaDelegator

    // >>> ContentHandler

    public void startElement (final String uri,
			      final String local_name,
			      final String name,
			      final Attributes attrs)
    {
	final PipelineState state = pipeline.getState ();

	element_characters.setLength (0);

	switch (state) {
	case OPEN:
	    if (delegate.openStream (name, attrs))
		pipeline.validate ();
	    else
		pipeline.invalidate ("Invalid stream", null);
	    break;
	case VALID:
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
	final PipelineState state = pipeline.getState ();
	final String data = (0 == element_characters.length ()
			     ? null
			     : element_characters.toString ());

	element_characters.setLength (0);

	switch (state) {
	case VALID:
	    if (null == stanza) {
		delegate.closeStream ();
		pipeline.startClosing ();
	    } else {
		if (!(stanza.getName ().equals (name))) {
		    pipeline.invalidate ("Unbalanced element found", null);
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

    // <<< ContentHandler
}
