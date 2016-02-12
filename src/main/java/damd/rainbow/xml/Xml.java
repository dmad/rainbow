package damd.rainbow.xml;

import java.util.List;
import java.util.Vector;

import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class Xml
{
    private static DocumentBuilder doc_builder = null;

    public static DocumentBuilder getDocumentBuilder ()
	throws XmlException
    {
	if (null == doc_builder) {
	    synchronized (Xml.class) {
		if (null == doc_builder) {
		    try {
			doc_builder = DocumentBuilderFactory.newInstance ()
			    .newDocumentBuilder ();
		    } catch (ParserConfigurationException e) {
			throw new XmlException (e);
		    }
		}
	    }
	}

	return doc_builder;
    }

    protected static String node_to_path (Node node)
    {
	StringBuffer path = null;

	if (null != node) {
	    path = new StringBuffer (node.getNodeName ());
	    for (node = node.getParentNode (); null != node; node = node
		     .getParentNode ())
		path.insert (0, node.getNodeName () + "/");
	}

	return null == path ? "<null>" : path.toString ();
    }

    public static Document newDocument ()
	throws XmlException
    {
	return getDocumentBuilder ().newDocument ();
    }

    public static Element createElement (Document doc,
					 Node parent,
					 String name,
					 String value)
    {
	if (null == doc)
	    throw new NullPointerException ("doc");

	if (null == name)
	    throw new NullPointerException ("name");

	Element element = doc.createElement (name);

	if (null != parent)
	    parent.appendChild (element);

	if (null != value) {
	    Text text = doc.createTextNode (value);

	    element.appendChild (text);
	}

	return element;
    }

    public static Document parse (InputStream stream)
	throws XmlException,
	       IOException
    {
	Document doc;

	try {
	    doc = getDocumentBuilder ().parse (stream);
	} catch (SAXException e) {
	    throw new XmlException (e);
	}

	return doc;
    }

    public static Document parse (String filename)
	throws XmlException,
	       IOException
    {
	Document doc;

	try {
	    doc = getDocumentBuilder ().parse (filename);
	} catch (SAXException e) {
	    throw new XmlException (e);
	}

	return doc;
    }

    public static void serialize (Node node, StringBuffer sb)
    {
	if (null == node) {
	    // empty
	} else if (node instanceof Element) {
	    String name = node.getNodeName ();
	    NamedNodeMap atts = node.getAttributes ();

	    sb.append ("<" + name);
	    if (atts.getLength () > 0) {
		for (int a = 0;a < atts.getLength ();++a) {
		    Node att = atts.item (a);

		    sb.append (" " + att.getNodeName ());
		    sb.append ("=\"" + att.getNodeValue () + "\"");
		}
	    }
	    sb.append ('>');

	    for (Node child = node.getFirstChild ();
		 null != child;
		 child = child.getNextSibling ())
		serialize (child, sb);

	    sb.append ("</" + name + ">");
	} else if (node instanceof Text) {
	    sb.append (node.getNodeValue ());
	    assert (null == node.getFirstChild ());
	} else if (node instanceof Document) {
	    serialize (node.getFirstChild (), sb);
	}
    }

    public static String serialize (Node node)
    {
	StringBuffer sb = new StringBuffer ();

	serialize (node, sb);

	return sb.toString ();
    }

    public static void assertElementName (Element element,
					  String name)
	throws XmlUnexpectedElementNameException
    {
	if (null == element)
	    throw new NullPointerException ("element");

	if (null == name)
	    throw new NullPointerException ("name");

	if (!(name.equals (element.getTagName ())))
	    throw new XmlUnexpectedElementNameException (element, name);
    }

    public static List<Element> getChildElements (Node parent,
						  String name)
	throws XmlException
    {
	List<Element> children;

	if (null == parent)
	    throw new NullPointerException ("parent");

	if (null == name)
	    throw new NullPointerException ("name");

	children = new Vector<Element> ();
	for (Node c = parent.getFirstChild (); null != c; c = c
		 .getNextSibling ())
	    if (c instanceof Element
		&& ((Element) c).getTagName ().equals (name))
		children.add ((Element) c);

	return children;
    }

    public static Element getUniqueElement (Node parent,
					    String name,
					    boolean mandatory)
	throws XmlMissingElementException,
	       XmlException
    {
	Element found = null;

	if (null == parent)
	    throw new NullPointerException ("parent");

	if (null == name)
	    throw new NullPointerException ("name");

	for (Node n = parent.getFirstChild ();
	     null != n;
	     n = n.getNextSibling ())
	    if (n instanceof Element && name.equals (n.getNodeName ()))
		if (null == found)
		    found = (Element) n;
		else
		    throw new XmlException
			("The element(parent("
			 + node_to_path (parent)
			 + ") name("
			 + name
			 + ")) is not unique");

	if (mandatory && null == found)
	    throw new XmlMissingElementException (parent, name);

	return found;
    }

    public static String getAttributeValue (Element element,
					    String attribute_name,
					    boolean mandatory)
	throws XmlMissingAttributeException
    {
	if (null == element)
	    throw new NullPointerException ("element");

	if (null == attribute_name)
	    throw new NullPointerException ("attribute_name");

	String value = element.getAttribute (attribute_name);

	if (null != value && value.isEmpty ())
	    value = null;

	if (null == value && mandatory)
	    throw new XmlMissingAttributeException (element, attribute_name);

	return value;
    }

    public static String getElementValue (Element element,
					  boolean mandatory)
	throws XmlException
    {
	StringBuffer value = null;

	if (null != element) {
	    for (Node n = element.getFirstChild ();
		 null != n;
		 n = n.getNextSibling ()) {
		if (n instanceof Text) {
		    if (null == value)
			value = new StringBuffer (n.getNodeValue ());
		    else
			value.append (n.getNodeValue ());
		}
	    }
	}

	if (mandatory && null == value)
	    throw new XmlException
		("The element("
		 + node_to_path (element)
		 + ") has no value");

	return null == value ? null : value.toString ();
    }

    public static String getUniqueElementValue (Node parent,
						String element_name,
						boolean mandatory)
	throws XmlException
    {
	Element element = getUniqueElement (parent, element_name, mandatory);
	String value = null;

	if (null != element)
	    value = getElementValue (element, mandatory);

	return value;
    }

    public static Long getElementValueAsLong (Element element,
					      boolean mandatory)
	throws XmlException
    {
	String sv = getElementValue (element, mandatory);
	Long lv = null;

	if (null != sv)
	    try {
		lv = Long.valueOf (sv);
	    } catch (NumberFormatException e) {
		throw new XmlException
		    ("The long value of element("
		     + node_to_path (element)
		     + ") had an invalid format (reason("
		     + e.getMessage ()
		     + "))");
	    }

	return lv;
    }

    public static Double getElementValueAsDouble (Element element,
						  boolean mandatory)
	throws XmlException
    {
	String sv = getElementValue (element, mandatory);
	Double dv = null;

	if (null != sv)
	    try {
		dv = Double.valueOf (sv);
	    } catch (NumberFormatException e) {
		throw new XmlException
		    ("The double value of element("
		     + node_to_path (element)
		     + ") had an invalid format (reason("
		     + e.getMessage ()
		     + "))");
	    }

	return dv;
    }
}
