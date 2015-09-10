package damd.rainbow.net.pipeline;

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

    private Selector channel_selector; // not synchronized

    // >>> select thread (~ select_future) only, no synchronization

    private ByteBuffer inbound_buffer;
    private ByteBuffer outbound_buffer;

    private SelectionKey selection_key;

    private Future<?> target_future;

    // <<< select thread

    public PipelineSocketHandler (final ExecutorService select_executor,
				  final ExecutorService target_executor)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.select_executor = select_executor;
	this.target_executor = target_executor;
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

	switch (phase) {
	case 0:
	    inbound_buffer = ByteBuffer.allocateDirect (20480);
	    outbound_buffer = ByteBuffer.allocateDirect (20480);

	    channel_selector = Selector.open ();
	    selection_key = channel.register (channel_selector,
					      SelectionKey.OP_READ);
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

    public synchronized void handleTargetEvent (final PipelineEvent event)
    {
	switch (event) {
	case OUTBOUND_AVAILABLE:
	    if (null != select_future && !(select_future.isDone ()))
		channel_selector.wakeup ();
	    break;
	}
    }

    // <<< PipelineSource

    // >>> SocketHandler

    public synchronized void open (final SocketChannel channel,
				   final SocketListener listener)
	throws IOException
    {
	if (null != select_future && !(select_future.isDone ()))
	    throw new IllegalStateException ("Already open");

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

    private void cleanup ()
    {
	inbound_buffer = null;
	outbound_buffer = null;

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
	    IOException,
	    Exception
    {
	boolean keep_going = pipeline.isUsable ();

	if (!keep_going)
	    return keep_going;

	if (outbound_buffer.hasRemaining ())
	    target.giveOutbound (outbound_buffer);

	selection_key.interestOps
	    (0 == outbound_buffer.position ()
	     ? selection_key.interestOps () & ~(SelectionKey.OP_WRITE)
	     : selection_key.interestOps () | SelectionKey.OP_WRITE);

	if (PipelineState.CLOSING == pipeline.getState ()) {
	    if (outbound_buffer.position () > 0) { // still need to write
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

		    inbound_buffer.compact ();
		    read = channel.read (inbound_buffer);

		    if (read < 0) // end of stream
			keep_going = false;
		    else if (read > 0) {
			inbound_buffer.flip ();
			target_future = target_executor.submit
			    (new Runnable () {
				    public void run ()
				    {
					try {
					    target.handleInbound
						(inbound_buffer);
					} catch (Throwable x) {
					    pipeline.invalidate
						("While handling input", x);
					}
				    }
				});
		    }
		}

		if (selection_key.isWritable ()
		    && outbound_buffer.position () > 0) {
		    outbound_buffer.flip ();
		    try {
			channel.write (outbound_buffer);
		    } finally {
			outbound_buffer.compact ();
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