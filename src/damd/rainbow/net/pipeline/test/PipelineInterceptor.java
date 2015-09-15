package damd.rainbow.net.pipeline.test;

import java.util.logging.Logger;

import java.nio.ByteBuffer;

import damd.rainbow.net.pipeline.PipelineState;
import damd.rainbow.net.pipeline.PipelineEvent;
import damd.rainbow.net.pipeline.Pipeline;
import damd.rainbow.net.pipeline.PipelineSource;
import damd.rainbow.net.pipeline.PipelineTarget;

public class PipelineInterceptor
    implements
	PipelineSource,
	PipelineTarget
{
    private Logger logger;

    private Pipeline pipeline;
    private PipelineSource source;
    private PipelineTarget target;

    public PipelineInterceptor ()
    {
	logger = Logger.getLogger (getClass ().getName ());
    }

    // >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
	logger.info ("setPipeline called");
    }

    public void stateHasChanged (final PipelineState new_state)
    {
	// We do not need to do anything an a pipepline state change
    }

    public void openNode (short phase)
    {
	logger.info ("openNode called with phase(" + phase + ")");
    }

    public void closeNode ()
    {
	logger.info ("closeNode called");
    }

    // <<< PipelineNode

    // >>> PipelineSource

    public void setTarget (final PipelineTarget target)
    {
	this.target = target;
	logger.info ("setTarget called");
    }

    public void handleTargetEvent (final PipelineEvent event)
    {
	logger.info ("handleTargetEvent called with event(" + event + ")");
	source.handleTargetEvent (event);
    }

    // <<< PipelineSource

    // >>> PipelineTarget

    public void setSource (final PipelineSource source)
    {
	this.source = source;
	logger.info ("setSource called");
    }

    public void handleInbound (final ByteBuffer buffer)
	throws Exception
    {
	logger.info ("handleInbound called with buffer("
		     + byteBufferToString (buffer, false)
		     + ")");
	target.handleInbound (buffer);
    }

    public void giveOutbound (final ByteBuffer buffer)
	throws Exception
    {
	target.giveOutbound (buffer);
	logger.info ("giveOutbound called with buffer("
		     + byteBufferToString (buffer, true)
		     + ")");
    }

    // <<< PipelineTarget

    public static String byteBufferToString (final ByteBuffer buffer,
					     final boolean flip)
    {
	final int prev_pos = buffer.position ();
	final int prev_limit = buffer.limit ();
	final StringBuilder output;

	//if (flip)
	//  buffer.flip ();

	output = new StringBuilder ()
	    .append ("position(" + buffer.position ()
		     + ") limit(" + buffer.limit ()
		     + ") remaining(" + buffer.remaining ()
		     + ") needs flip(" + flip
		     + ")");
	/*
		     + ") buffer(");
	for (int i = 0;i < 40 && buffer.hasRemaining ();++i) {
	    final byte b = buffer.get ();

	    output.append (Integer.toHexString ((int) b));
	    if (buffer.hasRemaining ())
		output.append (" ");
	}
	output.append (")");
	*/

	//buffer.limit (prev_limit);
	//buffer.position (prev_pos);

	return output.toString ();
    }
}
