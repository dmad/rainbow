package damd.rainbow.xml;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

import org.xml.sax.SAXException;

public class XmlException
    extends Exception
{
    private static final long serialVersionUID = 1L;
    
    protected Node node;
    protected String context; // name of attribute, node

    private static String getContextDescription (Node node, String context)
    {
	StringBuilder sb = new StringBuilder ();

	if (null != node) {
	    StringBuilder path = new StringBuilder (node.getNodeName ());
	    
	    for (Node p = node.getParentNode ();
		 null != p;
		 p = p.getParentNode ())
		path.insert (0, p.getNodeName () + "/");
	    
	    sb.append ("[" + path + "]");
	}
	
	if (null != context)
	    sb.append ("(" + context + ")");

	return sb.length () > 0 ? sb.toString () : "<no context available>";
    }

    public XmlException ()
    {
	super ();
    }
    
    public XmlException (String msg)
    {
	super (msg);
    }
    
    public XmlException (Node node, 
			 String context, 
			 Throwable cause)
    {
	super ("context(" + getContextDescription (node, context) + ")",
	       cause);

	this.node = node;
	this.context = context;
    }

    public XmlException (Node node, 
			 String context, 
			 String msg,
			 Throwable cause)
    {
	super ("message(" + msg + ") context(" 
	       + getContextDescription (node, context) + ")",
	       cause);
	
	this.node = node;
	this.context = context;
    }

    public XmlException (ParserConfigurationException e)
    {
	super (e);
    }

    public XmlException (SAXException e)
    {
	super (e);
    }

    public XmlException (DOMException e)
    {
	super (e);
    }
}    
