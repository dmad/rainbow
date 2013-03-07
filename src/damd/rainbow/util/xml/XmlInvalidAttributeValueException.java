package damd.rainbow.util.xml;

import org.w3c.dom.Element;

public class XmlInvalidAttributeValueException
    extends XmlException
{
    private static final long serialVersionUID = 1L;
    
    public XmlInvalidAttributeValueException (Element element,
					      String attribute_name,
					      Throwable cause)
    {
	super (element, attribute_name, cause);
    }
}
