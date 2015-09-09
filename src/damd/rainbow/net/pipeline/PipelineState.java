package damd.rainbow.net.pipeline;

public enum PipelineState
{
    INVALID, // OPEN or CLOSED but its state is invalid
    CLOSED,  // CLOSED logically and physically
    OPEN,    // OPEN physically but not logically yet (no data yet)
    VALID,   // OPEN physically and logically
    CLOSING  // VALID but no inbound data accepted anymore (=> CLOSED)
}
