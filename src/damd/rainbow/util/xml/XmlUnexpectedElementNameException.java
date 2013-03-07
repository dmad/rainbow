package damd.rainbow.util.xml;

import org.w3c.dom.Element;

public class XmlUnexpectedElementNameException
    extends XmlException
{
    private static final long serialVersionUID = 1L;
    
    public XmlUnexpectedElementNameException (Element element,
					      String expected_name)
    {
	super (element, expected_name, null);
    }
}
