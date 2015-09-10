package damd.rainbow.net.pipeline;

import java.util.Deque;
import java.util.ArrayDeque;

import java.nio.ByteBuffer;

public class BufferedOutbound
{
    private final PipelineSource source;
    private final Deque<ByteBuffer> buffers;

    public BufferedOutbound (final PipelineSource source)
    {
	this.source = source; // may be null
	buffers = new ArrayDeque<> ();
    }

    public synchronized void write (final ByteBuffer input)
    {
	if (null != input && input.hasRemaining ())
	    buffers.offerLast (input);

	if (null != source)
	    source.handleTargetEvent (PipelineEvent.OUTBOUND_AVAILABLE);
    }

    public synchronized void giveOutbound (final ByteBuffer output)
    {
	final ByteBuffer src = buffers.pollFirst ();

	if (null != src) {
	    if (src.remaining () > output.remaining ()) {
		// We can only partially put the src into output
		int prev_limit = src.limit ();

		src.limit (src.position () + output.remaining ());
		output.put (src);
		src.limit (prev_limit);
	    } else
		output.put (src);

	    if (src.hasRemaining ()) // put remainder back on queue
		buffers.offerFirst (src);
	}
    }
}
