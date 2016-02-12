package damd.rainbow.net.pipeline;

public interface PipelineNode
{
    public void setPipeline (Pipeline pipeline);

    public void stateHasChanged (PipelineState new_state);

    public void openNode (short phase)
	throws Exception;
    public void closeNode ();
}
