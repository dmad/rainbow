package damd.rainbow.util.xml;

import org.w3c.dom.Element;

public class XmlMissingAttributeException
    extends XmlException
{
    private static final long serialVersionUID = 1L;
    
    public XmlMissingAttributeException (Element element, String attribute_name)
    {
	super (element, attribute_name, null);
    }
}
