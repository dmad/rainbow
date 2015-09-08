package damd.rainbow.net.pipeline;

import java.util.logging.Logger;

import java.nio.ByteBuffer;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class PipelineSSLHandler
    implements
	PipelineTarget,
	PipelineSource
{
    private Logger logger;

    private Pipeline pipeline;
    private PipelineTarget target;
    private PipelineSource source;

    private boolean client_mode;
    private SSLEngine engine;

    public PipelineSSLHandler ()
    {
	logger = Logger.getLogger (getClass ().getName ());
    }

    // >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
    }

    public void openNode (final short phase)
	throws NoSuchAlgorithmException
    {
	switch (phase) {
	case 0:
	    engine = SSLContext.getInstance ("TLSv1.2").createSSLEngine ();
	    engine.setUseClientMode (client_mode);
	    break;
	}
    }

    public void closeNode ()
    {
    }

    // <<< PipelineNode

    // >>> PipelineTarget

    public void setSource (final PipelineSource source)
    {
	this.source = source;
    }

    public void handleInbound (final ByteBuffer data)
	throws Exception
    {
    }

    // <<< PipelineTarget

    // >>> PipelineSource

    public void setTarget (final PipelineTarget target)
    {
	this.target = target;
    }

    public void writeOutbound (final ByteBuffer value)
    {
    }

    // <<< PipelineSource
}
