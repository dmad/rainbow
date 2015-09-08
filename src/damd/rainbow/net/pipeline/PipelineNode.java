package damd.rainbow.net.pipeline;

public interface PipelineNode
{
    public void setPipeline (Pipeline pipeline);

    public void openNode (short phase)
	throws Exception;
    public void closeNode ();
}
