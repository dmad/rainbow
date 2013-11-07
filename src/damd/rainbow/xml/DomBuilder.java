package damd.rainbow.xml;

import java.util.NoSuchElementException;
import java.util.ArrayDeque;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class DomBuilder
{
    private static DocumentBuilder doc_builder = null;

    public static DocumentBuilder getDocumentBuilder ()
	throws XmlException
    {
	if (null == doc_builder) {
	    synchronized (DomBuilder.class) {
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

    private Document document;
    private Element current_element;

    private boolean cascade_next_element;

    private ArrayDeque<Element> bookmarks;

    public DomBuilder (String name)
	throws XmlException
    {
	document = getDocumentBuilder ().newDocument ();

	current_element = document.createElement (name);
	document.appendChild (current_element);

	cascade_next_element = true;
    }

    public Document getDocument ()
    {
	return document;
    }

    public String getName ()
    {
	return current_element.getTagName ();
    }

    public String serialize ()
    {
	return new DomReader (document).serialize ();
    }

    public DomBuilder push ()
    {
	if (null == bookmarks)
	    bookmarks = new ArrayDeque<Element> ();

	bookmarks.push (current_element);

	return this;
    }

    public DomBuilder pop ()
	throws NoSuchElementException
    {
	if (null == bookmarks)
	    throw new NoSuchElementException
		("No bookmarks where ever created");

	current_element = bookmarks.pop ();
	cascade_next_element = false;

	return this;
    }

    public DomBuilder addAttribute (String name, String value)
    {
	current_element.setAttribute (name, value);

	return this;
    }

    public DomBuilder addText (String text)
    {
	if (null != text) {
	    Text tnode = document.createTextNode (text);

	    current_element.appendChild (tnode);
	}

	return this;
    }

    public DomBuilder addChildElement (String name)
    {
	Element child = document.createElement (name);

	current_element.appendChild (child);
	current_element = child;

	cascade_next_element = false;

	return this;
    }

    public DomBuilder cascadeNextElement (boolean cascade)
    {
	cascade_next_element = cascade;

	return this;
    }

    public DomBuilder addElement (String name)
    {
	if (cascade_next_element)
	    addChildElement (name);
	else
	    moveToParent ().addChildElement (name);

	return this;
    }

    public DomBuilder moveToRoot ()
    {
	current_element = document.getDocumentElement ();

	return this;
    }

    public boolean hasParent ()
    {
	Node pnode = current_element.getParentNode ();

	return Node.ELEMENT_NODE == pnode.getNodeType ();
    }

    public DomBuilder moveToParent ()
    {
	Node pnode = current_element.getParentNode ();

	if (Node.ELEMENT_NODE == pnode.getNodeType ())
	    current_element = (Element) pnode;
	else
	    throw new IllegalStateException ();

	return this;
    }
}
