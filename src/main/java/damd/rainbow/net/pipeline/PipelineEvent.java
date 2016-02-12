package damd.rainbow.net.pipeline;

public enum PipelineEvent
{
    OUTBOUND_AVAILABLE, // target -> source
    NEED_INBOUND;       // target -> source
}
