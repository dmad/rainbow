package damd.rainbow;

public class Version
{
    public static String asString ()
    {
	return Version.class.getPackage ().getImplementationVersion ();
    }

    public static void main (String[] args)
    {
	System.out.println (Version.asString ());
    }
}
