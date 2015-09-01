package damd.rainbow.net;

import java.nio.ByteBuffer;

public interface BytePipelineSource
{
    public ConnectionState getState ();
    public void setState (ConnectionState state);
    public void write (ByteBuffer output);
}
