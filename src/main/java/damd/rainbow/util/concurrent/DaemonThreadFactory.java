package damd.rainbow.util.concurrent;

import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory
    implements ThreadFactory
{
    // >>> ThreadFactory

    public Thread newThread (final Runnable r)
    {
	final Thread t = new Thread (r);

	t.setDaemon (true);

	return t;
    }

    // <<< ThreadFactory
}
