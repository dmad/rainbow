package damd.rainbow.net.pipeline.test;

import java.util.logging.Logger;

import java.nio.ByteBuffer;

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

    public void handleInbound (final ByteBuffer data)
	throws Exception
    {
	final int prev_pos = data.position ();
	final StringBuilder output;

	output = new StringBuilder ("handleInbound called with")
	    .append (" position(" + data.position ()
		     + ") limit(" + data.limit ()
		     + ") data(");
	while (data.hasRemaining ()) {
	    final byte b = data.get ();

	    output.append (Byte.toString (b));
	    if (data.hasRemaining ())
		output.append (" ");
	}
	output.append (")");

	data.position (prev_pos);

	logger.info (output.toString ());
	target.handleInbound (data);
    }

    public void giveOutbound (final ByteBuffer data)
	throws Exception
    {
	logger.info ("giveOutbound called");
	target.giveOutbound (data);
    }

    // <<< PipelineTarget
}
