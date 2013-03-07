package damd.rainbow.util.xml;

import java.util.Queue;
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

    private Queue<Element> bookmarks;

    public DomBuilder (String name)
	throws XmlException
    {
	document = getDocumentBuilder ().newDocument ();

	current_element = document.createElement (name);
	document.appendChild (current_element);

	bookmarks = new ArrayDeque<Element> ();
    }

    public Document getDocument ()
    {
	return document;
    }

    public void push ()
    {
	bookmarks.add (current_element);
    }

    public void pop ()
    {
	current_element = bookmarks.remove ();
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

	return this;
    }

    public DomBuilder addSiblingElement (String name)
    {
	return moveToParent ().addChildElement (name);
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
