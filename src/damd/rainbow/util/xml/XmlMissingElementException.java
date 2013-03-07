package damd.rainbow.util.xml;

import org.w3c.dom.Node;

public class XmlMissingElementException
    extends XmlException
{
    private static final long serialVersionUID = 1L;
    
    public XmlMissingElementException (Node parent, 
				       String element_name)
    {
	super (parent, element_name, null);
    }
}

