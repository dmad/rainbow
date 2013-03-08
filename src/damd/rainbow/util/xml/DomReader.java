package damd.rainbow.util.xml;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.DOMConfiguration;

import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

public class DomReader
{
    private boolean empty_string_is_a_value;

    private Element current_element;

    public DomReader (Element element)
    {
	empty_string_is_a_value = false;
	current_element = element;
    }

    public DomReader (Document document)
    {
	empty_string_is_a_value = false;
	current_element = document.getDocumentElement ();
    }

    public boolean emptyStringIsAValue ()
    {
	return empty_string_is_a_value;
    }

    public void setEmptyStringIsAValue (boolean is_a_value)
    {
	empty_string_is_a_value = is_a_value;
    }

    private boolean stringHasAValue (final String string)
    {
	return null == string
	    ? false
	    : (string.isEmpty ()
	       ? empty_string_is_a_value
	       : true);
    }

    public String serialize ()
    {
	String xml = null;
	Document doc;

	if (null != (doc = current_element.getOwnerDocument ())) {
	    DOMImplementationLS dom_impl;
	    DOMConfiguration dom_conf;
	    LSSerializer serializer;

	    dom_impl = (DOMImplementationLS) doc.getImplementation ();
	    serializer = dom_impl.createLSSerializer ();
	    dom_conf = serializer.getDomConfig ();
	    dom_conf.setParameter ("format-pretty-print", true);
	    dom_conf.setParameter ("xml-declaration", false);

	    xml = serializer.writeToString (current_element);
	}

	return xml;
    }

    public String getName ()
    {
	return current_element.getTagName ();
    }

    public String getAttribute (String name, boolean mandatory)
	throws XmlMissingAttributeException
    {
	String value = current_element.getAttribute (name);

	if (mandatory && !(stringHasAValue (value)))
	    throw new XmlMissingAttributeException (current_element,
						    name);

	return value;
    }

    public String getText (boolean mandatory)
	throws XmlMissingValueException
    {
	StringBuilder sb = null;
	String text;

	for (Node child = current_element.getFirstChild ();
	     null != child;
	     child = child.getNextSibling ()) {
	    if (Node.TEXT_NODE == child.getNodeType())
		if (null == sb)
		    sb = new StringBuilder (child.getNodeValue ());
		else
		    sb.append (child.getNodeValue ());
	}

	text = (null == sb ? null : sb.toString ());

	if (mandatory && !(stringHasAValue (text)))
	    throw new XmlMissingValueException (current_element);

	return text;
    }

    public void moveToParent ()
    {
	Node parent = current_element.getParentNode ();

	if (Node.ELEMENT_NODE == parent.getNodeType ())
	    current_element = (Element) parent;
	else
	    throw new IllegalStateException ();
    }

    public boolean moveToChild (boolean mandatory)
	throws XmlMissingElementException
    {
	boolean found = false;

	for (Node child = current_element.getFirstChild ();
	     !found && null != child;
	     child = child.getNextSibling ())
	    if (found = (Node.ELEMENT_NODE == child.getNodeType ()))
		current_element = (Element) child;

	if (mandatory && !found)
	    throw new XmlMissingElementException (current_element, "[child]");

	return found;
    }

    public boolean moveToNext (boolean mandatory)
	throws XmlMissingElementException
    {
	boolean found = false;

	for (Node sibling = current_element.getNextSibling ();
	     !found && null != sibling;
	     sibling = sibling.getNextSibling ())
	    if (found = (Node.ELEMENT_NODE == sibling.getNodeType ()))
		current_element = (Element) sibling;

	if (mandatory && !found)
	    throw new XmlMissingElementException (current_element, "[next]");

	return found;
    }

    public boolean moveToNamedChild (String name, boolean mandatory)
	throws XmlMissingElementException
    {
	boolean found = false;

	for (Node child = current_element.getFirstChild ();
	     !found && null != child;
	     child = child.getNextSibling ())
	    if (Node.ELEMENT_NODE == child.getNodeType ()) {
		String child_name = child.getNodeName ();

		if (found = name.equals (child.getNodeName ()))
		    current_element = (Element) child;
	    }

	if (mandatory && !found)
	    throw new XmlMissingElementException (current_element, name);

	return found;
    }

    public boolean moveToNextNamed (boolean mandatory)
	throws XmlMissingElementException
    {
	boolean found = false;

	for (Node sibling = current_element.getNextSibling ();
	     !found && null != sibling;
	     sibling = sibling.getNextSibling ())
	    if (Node.ELEMENT_NODE == sibling.getNodeType ()) {
		if (found = (current_element.getNodeName ()
			     .equals (sibling.getNodeName ())))
		    current_element = (Element) sibling;
	    }

	if (mandatory && !found)
	    throw new XmlMissingElementException
		(current_element.getParentNode (),
		 current_element.getNodeName ());

	return found;
    }
}
