package damd.rainbow.net.pipeline;

import java.nio.ByteBuffer;

public interface PipelineSource
    extends PipelineNode
{
    public void setTarget (PipelineTarget target);

    public void writeOutbound (ByteBuffer data);
}
