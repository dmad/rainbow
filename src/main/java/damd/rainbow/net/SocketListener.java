package damd.rainbow.net;

import java.util.HashSet;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;

import damd.rainbow.behavior.Engine;

public class SocketListener
    implements
	Runnable,
	Engine
{
    private Logger logger;

    private String name;

    private InetSocketAddress listener_address;
    private SocketHandlerFactory handler_factory;

    private ServerSocketChannel channel;
    private HashSet<SocketHandler> handlers;

    private Thread worker;

    public SocketListener (String name)
    {
	if (null == name)
	    throw new NullPointerException ("name");

	logger = Logger.getLogger (getClass ().getName ()
				   + ".instance(" + name + ")");

	this.name = name;

	handlers = new HashSet<SocketHandler> ();
    }

    public String toString ()
    {
	return getClass ().getName () + "(name(" + name + "))";
    }

    // >>> Named

    public String getName ()
    {
	return name;
    }

    public void setName (final String name)
	throws UnsupportedOperationException
    {
	throw new UnsupportedOperationException ();
    }

    // <<< Named

    private void checkState (State needed_state)
	throws IllegalStateException
    {
	if (needed_state != currentState ())
	    throw new IllegalStateException
		("This method can only be invoked when the listener is in"
		 + " state(" + needed_state + ")");
    }

    public void setListenerAddress (InetSocketAddress address)
	throws IllegalStateException
    {
	checkState (State.STOPPED);

	if (null != address && address.isUnresolved ())
	    throw new IllegalStateException ("Address is unresolved");

	listener_address = address;
    }

    public void setHandlerFactory (SocketHandlerFactory factory)
	throws IllegalStateException
    {
	checkState (State.STOPPED);

	this.handler_factory = factory;
    }

    public void removeHandler (SocketHandler handler)
    {
	synchronized (handlers) {
	    if (handlers.contains (handler)) {
		try {
		    handler.close ();
		} catch (IOException x) {
		    logger.log (Level.WARNING, "While closing handler", x);
		}
		handlers.remove (handler);
	    }
	}
    }

    // >>> Runnable

    public void run ()
    {
	logger.fine ("SocketListener worker thread has started");

	try {
	    while (true) {
		SocketChannel schannel;
		SocketHandler handler = null;

		schannel = channel.accept (); // blocks

		try {
		    handler = handler_factory.createSocketHandler ();

		    synchronized (handlers) {
			handlers.add (handler);
		    }

		    handler.open (schannel, this);
		} catch (final Throwable t) {
		    logger.log (Level.SEVERE,
				"While creating/opening SocketHandler("
				+ handler
				+ ")",
				t);
		    removeHandler (handler);
		    schannel.close ();
		}
	    }
	} catch (AsynchronousCloseException x) {
	    // Requested to stop (see changeState), swallow
	} catch (IOException x) {
	    logger.log (Level.SEVERE,
			"While waiting to accept a connection",
			x);
	}

	synchronized (this) {
	    try {
		channel.close ();
	    } catch (IOException x) {
		logger.log (Level.WARNING,
			    "While closing the channel",
			    x);
	    } finally {
		channel = null;
	    }
	}

	logger.fine ("SocketListener worker thread has stopped");
    }

    // <<< Runnable

    // >>> Engine

    public synchronized State currentState ()
    {
	return null != channel ? State.RUNNING : State.STOPPED;
    }

    public void changeState (State new_state)
	throws IOException
    {
	if (new_state != currentState ()) {
	    switch (new_state) {
	    case RUNNING:
		synchronized (this) {
		    try {
			ServerSocket socket;

			channel = ServerSocketChannel.open ();

			socket = channel.socket ();
			socket.setReuseAddress (true);

			socket.bind (listener_address);

			if (null == worker) {
			    worker = new Thread (this, toString ());
			    if (!(worker.isDaemon ()))
				worker.setDaemon (true);
			}

			worker.start ();
		    } catch (IOException x) {
			try {
			    if (null != channel)
				channel.close ();
			} finally {
			    channel = null;
			}

			throw x;
		    } catch (IllegalThreadStateException x) {
			try {
			    if (null != channel)
				channel.close ();
			} finally {
			    channel = null;
			}

			throw x;
		    }
		}
		break;
	    case STOPPED:
		synchronized (this) {
		    if (null != channel)
			channel.close ();
		}

		{
		    boolean stopped = false;

		    try {
			for (int i = 0;!stopped && i < 10;++i) {
			    Thread.sleep (500);

			    synchronized (this) {
				stopped = null == channel;
			    }
			}
		    } catch (InterruptedException x) {
			// swallow
		    }

		    if (!stopped)
			logger.severe ("Could not stop worker thread");
		}
		break;
	    }
	}
    }

    // <<< Engine
}
