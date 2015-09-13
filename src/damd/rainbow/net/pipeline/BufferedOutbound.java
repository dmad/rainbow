package damd.rainbow.net.pipeline;

import java.util.Deque;
import java.util.ArrayDeque;

import java.nio.ByteBuffer;

public class BufferedOutbound
{
    private final PipelineSource source;
    private final Deque<ByteBuffer> buffers;
    private boolean auto_flush;

    public BufferedOutbound (final PipelineSource source)
    {
	this.source = source; // may be null
	buffers = new ArrayDeque<> ();
	auto_flush = true;
    }

    public synchronized int remaining ()
    {
	int remaining = 0;

	for (final ByteBuffer buffer : buffers)
	    remaining += buffer.remaining ();

	return remaining;
    }

    public synchronized boolean autoFlush ()
    {
	return auto_flush;
    }

    public synchronized boolean setAutoFlush (final boolean auto_flush)
    {
	final boolean prev = this.auto_flush;

	this.auto_flush = auto_flush;

	return prev;
    }

    public synchronized void flush ()
    {
	if (null != source)
	    source.handleTargetEvent (PipelineEvent.OUTBOUND_AVAILABLE);
    }

    public synchronized void write (final ByteBuffer input)
    {
	if (null != input && input.hasRemaining ())
	    buffers.offerLast (input);

	if (null != source && auto_flush)
	    flush ();
    }

    public synchronized void giveOutbound (final ByteBuffer output)
    {
	ByteBuffer src;

	while (output.hasRemaining ()
	       && null != (src = buffers.pollFirst ())) {
	    if (src.remaining () > output.remaining ()) {
		// We can only partially put the src into output
		int prev_limit = src.limit ();

		src.limit (src.position () + output.remaining ());
		output.put (src);
		src.limit (prev_limit);

		if (src.hasRemaining ()) // put remainder back on queue
		    buffers.offerFirst (src);
	    } else
		output.put (src);
	}
    }
}
