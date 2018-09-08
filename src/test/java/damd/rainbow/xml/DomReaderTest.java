package damd.rainbow.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.w3c.dom.Document;

public class DomReaderTest
{
    @Test
    public void breakingOutOfStanzaIsNotAllowed ()
        throws Exception
    {
        final Document doc = new DomBuilder ("root")
            .addChildElement ("child1")
            .addChildElement ("child2")
            .getDocument ();
        final DomReader stanza;

        {
            final DomReader reader = new DomReader (doc);

            reader.moveToChild ();
            assertEquals (reader.getName (), "child1");

            stanza = reader.getStanzaReader (); // stanza is rooted at "child1"
        }

        stanza.moveToChild ();
        assertEquals (stanza.getName (), "child2");

        assertEquals (stanza.tryMoveToParent (), true); // child 1
        assertEquals (stanza.getName (), "child1");

        // we should *not* be able to go above child1
        assertEquals (stanza.tryMoveToParent (), false);
    }
}
