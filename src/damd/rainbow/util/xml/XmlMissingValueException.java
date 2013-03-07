package damd.rainbow.util.xml;

import org.w3c.dom.Element;

public class XmlMissingValueException
    extends XmlException
{
    private static final long serialVersionUID = 1L;
    
    public XmlMissingValueException (Element element)
    {
	super (element, null, null);
    }
}

