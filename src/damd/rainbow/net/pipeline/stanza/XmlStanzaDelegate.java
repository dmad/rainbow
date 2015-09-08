package damd.rainbow.net.pipeline.stanza;

import org.w3c.dom.Document;

import org.xml.sax.Attributes;

public interface XmlStanzaDelegate
{
    public void setDelegator (XmlStanzaDelegator delegator);

    public boolean openStream (String name, Attributes attrs);
    public void closeStream ();

    public void handleStanza (Document stanza);

    public void cleanup ();
}
