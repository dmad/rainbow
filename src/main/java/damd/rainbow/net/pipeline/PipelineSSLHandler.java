package damd.rainbow.net.pipeline;

import java.util.List;
import java.util.ArrayList;

import java.util.logging.Logger;

import java.nio.ByteBuffer;

import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngineResult;
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

    private List<KeyManager> key_mngrs;
    private List<TrustManager> trust_mngrs;
    private SecureRandom secure_random;
    private SSLEngine engine;

    private ByteBuffer inbound_buffer;
    private ByteBuffer outbound_buffer;

    public PipelineSSLHandler ()
    {
	logger = Logger.getLogger (getClass ().getName ());

	key_mngrs = new ArrayList<> ();
	trust_mngrs = new ArrayList<> ();
    }

    // >>> PipelineNode

    public void setPipeline (final Pipeline pipeline)
    {
	this.pipeline = pipeline;
    }

    public void stateHasChanged (final PipelineState new_state)
    {
	// We do not need to do anything an a pipepline state change
    }

    public void openNode (final short phase)
	throws
	    NoSuchAlgorithmException,
	    KeyManagementException
    {
	if (0 == phase) {
	    final SSLContext context = SSLContext.getInstance ("TLSv1.2");

	    context.init (key_mngrs.toArray (new KeyManager[0]),
			  trust_mngrs.toArray (new TrustManager[0]),
			  secure_random);
	    engine = context.createSSLEngine ();
	    engine.setUseClientMode (client_mode);
	    if (!client_mode)
		engine.setNeedClientAuth (false);

	    inbound_buffer = ByteBuffer.allocate
		(engine.getSession ().getApplicationBufferSize ());
	    outbound_buffer = ByteBuffer.allocate
		(engine.getSession ().getApplicationBufferSize ());
	}
    }

    public void closeNode ()
    {
	// TODO: implement
    }

    // <<< PipelineNode

    // >>> PipelineTarget

    public void setSource (final PipelineSource source)
    {
	this.source = source;
    }

    public void handleInbound (final ByteBuffer buffer)
	throws Exception
    {
	final SSLEngineResult result;

	result = engine.unwrap (buffer, inbound_buffer);

	switch (result.getHandshakeStatus ()) {
	case NEED_TASK:
	    runDelegatedTasks (result);
	    break;
	case NEED_UNWRAP:
	    source.handleTargetEvent (PipelineEvent.NEED_INBOUND);
	    break;
	case NEED_WRAP:
	    source.handleTargetEvent (PipelineEvent.OUTBOUND_AVAILABLE);
	    break;
	}

	if (inbound_buffer.position () > 0) {
	    inbound_buffer.flip ();
	    try {
		target.handleInbound (inbound_buffer);
	    } finally {
		inbound_buffer.compact ();
	    }
	}
    }

    public void giveOutbound (final ByteBuffer buffer)
	throws Exception
    {
	if (outbound_buffer.hasRemaining ())
	    target.giveOutbound (outbound_buffer);

	outbound_buffer.flip ();

	/* always run engine.wrap as it may produce data
	   even if there is no data from the target */
	try {
	    final SSLEngineResult result;

	    result = engine.wrap (outbound_buffer, buffer);

	    switch (result.getHandshakeStatus ()) {
	    case NEED_TASK:
		runDelegatedTasks (result);
		break;
	    case NEED_UNWRAP:
		source.handleTargetEvent (PipelineEvent.NEED_INBOUND);
		break;
	    }
	} finally {
	    outbound_buffer.compact ();
	}
    }

    // <<< PipelineTarget

    // >>> PipelineSource

    public void setTarget (final PipelineTarget target)
    {
	this.target = target;
    }

    public void handleTargetEvent (final PipelineEvent event)
    {
	source.handleTargetEvent (event);
    }

    // <<< PipelineSource

    public void addKeyManagers (final KeyManager[] key_mngrs)
    {
	for (final KeyManager key_mngr : key_mngrs)
	    this.key_mngrs.add (key_mngr);
    }

    public void addTrustManagers (final TrustManager[] trust_mngrs)
    {
	for (final TrustManager trust_mngr : trust_mngrs)
	    this.trust_mngrs.add (trust_mngr);
    }

    public void setSecureRandom (final SecureRandom rnd)
    {
	this.secure_random = rnd;
    }

    private void runDelegatedTasks (final SSLEngineResult result)
    {
	switch (result.getHandshakeStatus ()) {
	case NEED_TASK:
	    for (Runnable task = engine.getDelegatedTask ();
		 null != task;
		 task = engine.getDelegatedTask ())
		task.run ();
	    break;
	}
    }
}
