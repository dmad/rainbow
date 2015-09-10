package damd.rainbow.net.pipeline;

import java.util.ArrayList;

import java.util.logging.Logger;

import java.nio.ByteBuffer;

import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
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

    private ArrayList<KeyManager> key_mngrs;
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
	if (0 == phase) {
	    final SSLContext context = SSLContext.getInstance ("TLSv1.2");

	    engine = context.createSSLEngine ();
	    engine.setUseClientMode (client_mode);
	    if (!client_mode)
		engine.setNeedClientAuth (false);
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
	// TODO: implement
    }

    public void giveOutbound (final ByteBuffer data)
	throws Exception
    {
	// TODO: implement
    }

    // <<< PipelineTarget

    // >>> PipelineSource

    public void setTarget (final PipelineTarget target)
    {
	this.target = target;
    }

    public void handleTargetEvent (final PipelineEvent event)
    {
	// TODO: implement
    }

    // <<< PipelineSource

    public void addKeyManagers (final KeyManager[] mngrs)
    {
	for (final KeyManager key_mngr : key_mngrs)
	    this.key_mngrs.add (key_mngr);
    }
}
