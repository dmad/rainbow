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
	Runnable
{
    public interface Delegate
    {
	public void setDelegator (ByteSocketHandler delegator);

	public void handleInput (ByteBuffer data);

	public void cleanup ();
    }

    public enum State
    {
	INVALID,
	CLOSED,
	VALID,
	OPEN,
	CLOSING
    }

    private Logger logger;

    private String id;

    private final Delegate delegate;

    private ExecutorService select_executor;
    private ExecutorService delegate_executor;

    private SocketListener listener; // sychronized on 'this' for assignment
    private SocketChannel channel; // synchronized on 'this' for assignment

    private Future<?> select_future; // synchronized on 'this' for assignment

    private Deque<ByteBuffer> write_deque; // synchronized on itself

    private Selector channel_selector; // not synchronized

    // >>> select thread (~ select_future) only, no synchronization

    private ByteBuffer input_buffer;
    private ByteBuffer output_buffer;

    private SelectionKey selection_key;

    private State state;

    private Future<?> delegate_future;

    // <<< select thread

    public ByteSocketHandler (final ExecutorService select_executor,
			      final ExecutorService delegate_executor,
			      final Delegate delegate)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.delegate = delegate;
	this.select_executor = select_executor;
	this.delegate_executor = delegate_executor;

	write_deque = new ArrayDeque<ByteBuffer> ();
    }

    public String toString ()
    {
	return getClass ().getName () + "(id(" + id + "))";
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

    public void write (final byte[] value)
    {
	if (null != value && value.length > 0) {
	    synchronized (write_deque) {
		write_deque.offerLast (ByteBuffer.wrap (value));
	    }

	    synchronized (this) {
		if (null != select_future && !(select_future.isDone ()))
		    channel_selector.wakeup ();
	    }
	}
    }

    // >>> SocketHandler

    public synchronized void open (SocketChannel channel,
				   SocketListener listener)
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
	    StringBuilder sb = new StringBuilder ("channel(");

	    sb.append (channel.toString ());
	    sb.append (")");

	    if (null != listener) {
		sb.append (", listener(");
		sb.append (listener.toString ());
		sb.append (")");
	    }

	    id = sb.toString ();
	    logger = Logger.getLogger (getClass ().getName ()
				       + ".instance(" + id + ")");
	}

	delegate.setDelegator (this);

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
	input_buffer = ByteBuffer.allocateDirect (2048);
	output_buffer = ByteBuffer.allocateDirect (2048);
	selection_key = channel.register (channel_selector,
					  SelectionKey.OP_READ);

	state = State.VALID;
    }

    private void cleanup ()
    {
	delegate.cleanup ();

	input_buffer = null;
	output_buffer = null;

	if (null != channel_selector) {
	    try {
		channel_selector.close ();
	    } catch (IOException x) {
		logger.log (Level.WARNING,
			    "While closing channel_selector",
			    x);
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

	if (State.CLOSING == state)
	    if (output_buffer.position () > 0)
		selection_key.interestOps
		    (selection_key.interestOps ()
		     & ~(SelectionKey.OP_READ));
	    else
		state = State.CLOSED;

	if (state.ordinal () >= State.VALID.ordinal ()) {
	    if (channel_selector.select () > 0) {
		if ((0 != (selection_key.readyOps ()
			   & SelectionKey.OP_READ))
		    && (null == delegate_future
			|| delegate_future.isDone ())) {
		    int read = channel.read (input_buffer);

		    if (read < 0) // end of stream
			state = State.CLOSED;
		    else if (read > 0) {
			final ByteBuffer copy;

			input_buffer.flip ();
			copy  = ByteBuffer.allocate (input_buffer.remaining ());
			copy.put (input_buffer).flip ();
			input_buffer.clear ();

			delegate_future = delegate_executor.submit
			    (new Runnable () {
				    public void run ()
				    {
					delegate.handleInput (copy);
				    }
				});
		    }
		}

		if (0 != (selection_key.readyOps ()
			  & SelectionKey.OP_WRITE)
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
	try {
	    setup ();

	    while (state.ordinal () >= State.VALID.ordinal ())
		select ();
	} catch (ClosedSelectorException x) {
	    // swallow, requested to stop
	} catch (IOException x) {
	    logger.log (Level.WARNING, "While interacting with channel", x);
	} finally {
	    cleanup ();
	}
    }

    // <<< Runnable

}
