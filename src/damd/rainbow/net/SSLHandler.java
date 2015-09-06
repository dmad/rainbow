package damd.rainbow.net;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLHandler
    implements
	BytePipelineTarget,
	BytePipelineSource
{
    private final BytePipelineTarget target;
    private final BytePipelineSource source;

    private final SSLEngine engine;

    public SSLHandler (final BytePipelineTarget target,
		       final boolean client_mode)
    {
	logger = Logger.getLogger (getClass ().getName ());

	this.target = target;
	engine = SSLContext.getInstance ("TLSv1.2").createSSLEngine ();
    }

    // >>> BytePipelineTarget

    public void setSource (final BytePipelineSource source)
    {
	this.source = source;
    }

    public void handleInput (final ByteBuffer input)
	throws Exception
    {
    }

    public void cleanup ()
    {
    }

    // <<< BytePipelineTarget

    // >>> BytePipelineSource

    public ConnectionState getState ()
    {
	return source.getState ();
    }

    public void setState (final ConnectionState state)
    {
	source.setState (state);
    }

    public void write (final ByteBuffer value)
    {
    }

    // <<< BytePipelineSource
}
