package damd.rainbow.net.pipeline;

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

import damd.rainbow.net.SocketListener;
import damd.rainbow.net.SocketHandler;

public class PipelineSocketHandler
    implements
	SocketHandler,
	Runnable,
	PipelineSource
{
    private Logger logger;

    private String id;

    private final ExecutorService select_executor;
    private final ExecutorService target_executor;

    private Pipeline pipeline;
    private PipelineTarget target;

    private SocketListener listener; // sychronized on 'this' for assignment
    private SocketChannel channel; // synchronized on 'this' for assignment

    private Future<?> select_future; // synchronized on 'this' for assignment

    private final Deque<ByteBuffer> write_deque; // synchronized on itself

    private Selector channel_selector; // not synchronized

    // >>> select thread (~ select_future) only, no synchronization

    private ByteBuffer input_buffer;
    private ByteBuffer output_buffer;

    private SelectionKey selection_key;

    private Future<?> target_future;

    // <<< select thread

    public PipelineSocketHandler (final ExecutorService select_executor,
				  final ExecutorService target_executor)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.select_executor = select_executor;
	this.target_executor = target_executor;

	write_deque = new ArrayDeque<> ();
    }

    public String toString ()
    {
	return getClass ().getName () + "(id(" + id + "))";
    }

    // >>> PipelineNode

    public synchronized void setPipeline (final Pipeline pipeline)
    {
	assert (null == this.pipeline);

	this.pipeline = pipeline;
    }

    public synchronized void openNode (final short phase)
	throws IOException
    {
	assert (null != this.pipeline);
	assert (null != this.target);

	logger.info ("openNode called");

	switch (phase) {
	case 0:
	    if (null == channel_selector || !(channel_selector.isOpen ()))
		channel_selector = Selector.open ();
	    break;
	case 1:
	    select_future = select_executor.submit (this);
	    break;
	}
    }

    public synchronized void closeNode ()
    {
	try {
	    close ();
	} catch (IOException x) {
	    logger.log (Level.WARNING, "While closing node", x);
	}
    }

    // <<< PipelineNode

    // >>> PipelineSource

    public synchronized void setTarget (final PipelineTarget target)
    {
	this.target = target;
    }

    public void writeOutbound (final ByteBuffer value)
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

    // <<< PipelineSource

    // >>> SocketHandler

    public synchronized void open (final SocketChannel channel,
				   final SocketListener listener)
	throws IOException
    {
	logger.info ("open called");
	//if (null != select_future && !(select_future.isDone ()))
	//    throw new IllegalStateException ("Already open");

	if (channel.isBlocking ())
	    channel.configureBlocking (false);

	this.channel = channel;
	this.listener = listener;

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

	pipeline.open ();
    }

    public synchronized void close ()
	throws IOException
    {
	/* The thread (~ select_future) handles the connection, ask
	   it to stop (if it is not already stopped). */
	if (null != channel_selector
	    && null != select_future && !(select_future.isDone ()))
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
    }

    private void cleanup ()
    {
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

    private boolean select ()
	throws
	    IOException
    {
	boolean keep_going = pipeline.isUsable ();

	if (!keep_going)
	    return keep_going;

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

	if (PipelineState.CLOSING == pipeline.getState ()) {
	    if (output_buffer.position () > 0) { // still need to write
		if (0 != (selection_key.interestOps () & SelectionKey.OP_READ))
		    selection_key.interestOps
			(selection_key.interestOps ()
			 & ~(SelectionKey.OP_READ)); // stop reading
	    } else // stop socket handler
		keep_going = false;
	}

	if (keep_going) {
	    if (channel_selector.select () > 0) {
		if (selection_key.isReadable ()
		    && (null == target_future
			|| target_future.isDone ())) {
		    final int read;

		    input_buffer.compact ();
		    read = channel.read (input_buffer);

		    if (read < 0) // end of stream
			keep_going = false;
		    else if (read > 0) {
			input_buffer.flip ();
			target_future = target_executor.submit
			    (new Runnable () {
				    public void run ()
				    {
					try {
					    target.handleInbound (input_buffer);
					} catch (Throwable x) {
					    pipeline.invalidate
						("While handling input", x);
					}
				    }
				});
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

	return keep_going;
    }

    public void run ()
    {
	logger.fine ("Starting to handle socket");

	try {
	    setup ();

	    while (pipeline.isUsable ())
		if (!select ())
		    break;
	} catch (ClosedSelectorException x) {
	    // swallow, requested to stop
	} catch (IOException x) {
	    logger.log (Level.WARNING, "While interacting with channel", x);
	} catch (Throwable x) {
	    logger.log (Level.SEVERE, "While interacting with channel", x);
	} finally {
	    try {
		cleanup ();
		pipeline.close ();
	    } catch (Throwable x) {
		logger.log (Level.WARNING, "While closing pipeline", x);
	    }
	}

	logger.fine ("Stopped handling socket");
    }

    // <<< Runnable
}
