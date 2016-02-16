package damd.rainbow.xml;

import java.util.NoSuchElementException;
import java.util.ArrayDeque;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.DOMConfiguration;

import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;

public class DomReader
{
    private Element root_element;
    private Element current_element;

    private ArrayDeque<Element> bookmarks;

    public DomReader (Element element)
    {
	root_element = current_element = element;
    }

    public DomReader (Document document)
    {
	root_element = current_element = document.getDocumentElement ();
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

    public Element getElement ()
    {
	return current_element;
    }

    public String getName ()
    {
	return current_element.getTagName ();
    }

    public String getAttribute (String name, boolean mandatory)
	throws XmlMissingAttributeException
    {
	String value = current_element.getAttribute (name);

	/* Element.getAttribute returns an empty string if the
	   attribute is not specified, change it to null */
	if (null != value && value.isEmpty ())
	    value = null;

	if (mandatory && null == value)
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
	if (null != text) {
	    text = text.trim ();
	    if (text.isEmpty ())
		text = null;
	}

	if (mandatory && null == text)
	    throw new XmlMissingValueException (current_element);

	return text;
    }

    public DomReader push ()
    {
	if (null == bookmarks)
	    bookmarks = new ArrayDeque<Element> ();

	bookmarks.push (current_element);

	return this;
    }

    public DomReader pop ()
	throws NoSuchElementException
    {
	if (null == bookmarks)
	    throw new NoSuchElementException
		("No bookmarks where ever created");

	current_element = bookmarks.pop ();

	return this;
    }

    public DomReader moveToRoot ()
    {
	current_element = root_element;

	return this;
    }

    public boolean tryMoveToParent ()
    {
	boolean found = true;
	Node parent = current_element.getParentNode ();

	if (Node.ELEMENT_NODE == parent.getNodeType ())
	    current_element = (Element) parent;
	else
	    found = false;

	return found;
    }

   public DomReader moveToParent ()
	throws XmlMissingElementException
    {
	if (!tryMoveToParent ())
	    throw new XmlMissingElementException (current_element, "[parent]");

	return this;
    }

    public boolean tryMoveToChild ()
    {
	boolean found = false;

	for (Node child = current_element.getFirstChild ();
	     !found && null != child;
	     child = child.getNextSibling ())
	    if (found = (Node.ELEMENT_NODE == child.getNodeType ()))
		current_element = (Element) child;

	return found;
    }

    public DomReader moveToChild ()
	throws XmlMissingElementException
    {
	if (!tryMoveToChild ())
	    throw new XmlMissingElementException (current_element, "[child]");

	return this;
    }

    public boolean tryMoveToNext ()
    {
	boolean found = false;

	for (Node sibling = current_element.getNextSibling ();
	     !found && null != sibling;
	     sibling = sibling.getNextSibling ())
	    if (found = (Node.ELEMENT_NODE == sibling.getNodeType ()))
		current_element = (Element) sibling;

	return found;
    }

   public DomReader moveToNext ()
	throws XmlMissingElementException
    {
	if (!tryMoveToNext ())
	    throw new XmlMissingElementException (current_element, "[next]");

	return this;
    }

    public boolean tryMoveToNamedChild (String name)
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

	return found;
    }

    public DomReader moveToNamedChild (String name)
	throws XmlMissingElementException
    {
	if (!tryMoveToNamedChild (name))
	    throw new XmlMissingElementException (current_element, name);

	return this;
    }

    public boolean tryMoveToNextNamed ()
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

	return found;
    }

    public DomReader moveToNextNamed ()
	throws XmlMissingElementException
    {
	if (!tryMoveToNextNamed ())
	    throw new XmlMissingElementException
		(current_element.getParentNode (),
		 current_element.getNodeName ());

	return this;
    }

}
