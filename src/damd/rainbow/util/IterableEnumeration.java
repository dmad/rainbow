package damd.rainbow.util;

import java.util.Iterator;
import java.util.Enumeration;

/* From "Making Enumerations Iterable"
 * http://www.javaspecialists.eu/archive/Issue107.html
 */

public class IterableEnumeration<T>
    implements
	Iterable<T>
{
    private final Enumeration<T> en;
    
    public IterableEnumeration (Enumeration<T> en)
    {
	this.en = en;
    }

    public Iterator<T> iterator ()
    {
	return new Iterator<T> ()
	    {
		public boolean hasNext ()
		{
		    return en.hasMoreElements ();
		}

		public T next ()
		{
		    return en.nextElement ();
		}

		public void remove ()
		{
		    throw new UnsupportedOperationException ();
		}
	    };
    }

    public static <T> Iterable<T> make (Enumeration<T> en) 
    {
	return new IterableEnumeration<T>(en);
    }
}
