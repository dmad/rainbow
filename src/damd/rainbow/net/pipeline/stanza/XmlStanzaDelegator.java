package damd.rainbow.net.pipeline.stanza;

import org.w3c.dom.Document;

import damd.rainbow.xml.DomReader;

public interface XmlStanzaDelegator
{
    public void write (String value);
    public void write (DomReader stanza);
    public void write (Document stanza);
}
