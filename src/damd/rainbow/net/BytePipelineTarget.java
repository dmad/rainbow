package damd.rainbow.net;

import java.nio.ByteBuffer;

public interface BytePipelineTarget
{
    public void setSource (BytePipelineSource source);
    public void handleInput (ByteBuffer input)
	throws Exception;
    public void cleanup ();
}
