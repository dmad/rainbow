package damd.rainbow.net;

import java.util.Deque;
import java.util.ArrayDeque;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

public class ByteSocketHandler
    implements
	SocketHandler,
	Runnable,
	BytePipelineSource
{
    private Logger logger;

    private String id;

    private final ExecutorService select_executor;
    private final ExecutorService target_executor;

    private final BytePipelineTarget target;

    private SocketListener listener; // sychronized on 'this' for assignment
    private SocketChannel channel; // synchronized on 'this' for assignment

    private Future<?> select_future; // synchronized on 'this' for assignment

    private final Deque<ByteBuffer> write_deque; // synchronized on itself

    private Selector channel_selector; // not synchronized

    private ConnectionState state; // synchronized on 'this'

    // >>> select thread (~ select_future) only, no synchronization

    private ByteBuffer input_buffer;
    private ByteBuffer output_buffer;

    private SelectionKey selection_key;

    private Future<?> target_future;

    // <<< select thread

    public ByteSocketHandler (final ExecutorService select_executor,
			      final ExecutorService target_executor,
			      final BytePipelineTarget target)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.select_executor = select_executor;
	this.target_executor = target_executor;
	this.target = target;

	write_deque = new ArrayDeque<ByteBuffer> ();
    }

    public String toString ()
    {
	return getClass ().getName () + "(id(" + id + "))";
    }

    // >>> BytePipelineSource

    public synchronized ConnectionState getState ()
    {
	return state;
    }

    public synchronized void setState (final ConnectionState state)
    {
	this.state = state;
    }

    public void write (final ByteBuffer value)
    {
	if (null != value && value.hasRemaining ())
	    synchronized (write_deque) {
		write_deque.offerLast (value.slice ());
	    }

	synchronized (this) {
	    if (null != select_future && !(select_future.isDone ()))
		channel_selector.wakeup ();
	}
    }

    // <<< BytePipelineSource

    // >>> SocketHandler

    public synchronized void open (final SocketChannel channel,
				   final SocketListener listener)
	throws IOException
    {
	if (null != select_future && !(select_future.isDone ()))
	    throw new IllegalStateException ("Already open");

	if (channel.isBlocking ())
	    channel.configureBlocking (false);

	this.listener = listener;
	this.channel = channel;

	if (null == channel_selector || !(channel_selector.isOpen ()))
	    channel_selector = Selector.open ();

	{
	    final StringBuilder sb;

	    sb = new StringBuilder ("channel(")
		.append (channel.toString ())
		.append (")");

	    if (null != listener) {
		sb
		    .append (", listener(")
		    .append (listener.toString ())
		    .append (")");
	    }

	    id = sb.toString ();
	    logger = Logger.getLogger (getClass ().getName ()
				       + ".instance(" + id + ")");
	}

	target.setSource (this);

	select_future = select_executor.submit (this);
    }

    public synchronized void close ()
	throws IOException
    {
	if (null != select_future && !(select_future.isDone ()))
	    channel_selector.close ();
    }

    // <<< SocketHandler

    // >>> Runnable

    private void setup ()
	throws IOException
    {
	input_buffer = ByteBuffer.allocateDirect (20480);
	output_buffer = ByteBuffer.allocateDirect (20480);
	selection_key = channel.register (channel_selector,
					  SelectionKey.OP_READ);

	state = ConnectionState.VALID;
    }

    private void cleanup ()
    {
	target.cleanup ();

	input_buffer = null;
	output_buffer = null;

	if (null != channel_selector) {
	    try {
		channel_selector.close ();
	    } catch (IOException x) {
		logger.log (Level.WARNING,
			    "While closing channel_selector",
			    x);
	    } finally {
		channel_selector = null;
	    }
	}

	synchronized (this) {
	    if (null != listener) {
		listener.removeHandler (this);
		listener = null;
	    }

	    if (null != channel) {
		try {
		    channel.close ();
		} catch (IOException x) {
		    logger.log (Level.WARNING,
				"While closing channel",
				x);
		} finally {
		    channel = null;
		}
	    }
	}
    }

    private void select ()
	throws
	    IOException
    {
	ConnectionState state_snapshot = getState ();

	synchronized (write_deque) {
	    if (output_buffer.hasRemaining ()
		&& !(write_deque.isEmpty ())) {
		ByteBuffer src = write_deque.pollFirst ();

		if (src.remaining () > output_buffer.remaining ()) {
		    int prev_src_limit = src.limit ();

		    src.limit (src.position () + output_buffer.remaining ());
		    output_buffer.put (src);
		    src.limit (prev_src_limit);
		} else
		    output_buffer.put (src);

		if (src.hasRemaining ())
		    write_deque.offerFirst (src);
	    }
	}

	selection_key.interestOps
	    (0 == output_buffer.position ()
	     ? selection_key.interestOps () & ~(SelectionKey.OP_WRITE)
	     : selection_key.interestOps () | SelectionKey.OP_WRITE);

	if (ConnectionState.CLOSING == state_snapshot)
	    if (output_buffer.position () > 0)
		selection_key.interestOps
		    (selection_key.interestOps ()
		     & ~(SelectionKey.OP_READ));
	    else
		setState (state_snapshot = ConnectionState.CLOSED);

	if (state_snapshot.ordinal () >= ConnectionState.VALID.ordinal ()) {
	    if (channel_selector.select () > 0) {
		if (selection_key.isReadable ()
		    && (null == target_future
			|| target_future.isDone ())) {
		    final int read;

		    input_buffer.compact ();
		    read = channel.read (input_buffer);

		    if (read < 0) // end of stream
			setState (state_snapshot =
				  ConnectionState.CLOSED);
		    else if (read > 0) {
			input_buffer.flip ();
			if (1 == 1) {
			    try {
				target.handleInput (input_buffer);
			    } catch (Exception x) {
				x.printStackTrace ();
			    }
			} else {
			target_future = target_executor.submit
			    (new Runnable () {
				    public void run ()
				    {
					try {
					    target.handleInput (input_buffer);
					} catch (Exception x) {
					    x.printStackTrace ();
					    logger.log
						(Level.WARNING,
						 "While handling input",
						 x);
					    setState (ConnectionState.INVALID);
					}
				    }
				});
			}
		    }
		}

		if (selection_key.isWritable ()
		    && output_buffer.position () > 0) {
		    output_buffer.flip ();
		    try {
			channel.write (output_buffer);
		    } finally {
			output_buffer.compact ();
		    }
		}

		channel_selector.selectedKeys ().clear ();
	    }
	}
    }

    public void run ()
    {
	logger.info ("Starting to handle socket");

	try {
	    setup ();

	    while (getState ().ordinal () >= ConnectionState.VALID.ordinal ())
		select ();
	} catch (ClosedSelectorException x) {
	    // swallow, requested to stop
	} catch (IOException x) {
	    logger.log (Level.WARNING, "While interacting with channel", x);
	} finally {
	    cleanup ();
	}

	logger.info ("Stopped handling socket");
    }

    // <<< Runnable
}
