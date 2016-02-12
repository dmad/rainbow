package damd.rainbow.net.pipeline;

import java.nio.ByteBuffer;

public interface PipelineTarget
    extends PipelineNode
{
    public void setSource (PipelineSource source);

    public void handleInbound (ByteBuffer data)
	throws Exception;

    public void giveOutbound (ByteBuffer data)
	throws Exception;
}
