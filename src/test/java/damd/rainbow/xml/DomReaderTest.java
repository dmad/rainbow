package damd.rainbow.xml;

import static org.hamcrest.CoreMatchers.*;

import org.junit.Test;

import static org.junit.Assert.*;

import org.w3c.dom.Document;

public class DomReaderTest
{
    @Test
    public void breakingOutOfStanza ()
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
            assertThat (reader.getName (), is (equalTo ("child1")));

            stanza = reader.getStanzaReader (); // stanza is rooted at "child1"
        }

        stanza.moveToChild ();
        assertThat (stanza.getName (), is (equalTo ("child2")));

        assertTrue (stanza.tryMoveToParent ()); // child1
        assertThat (stanza.getName (), is (equalTo ("child1")));

        // we should *not* be able to go above child1
        assertFalse (stanza.tryMoveToParent ());
    }
}
