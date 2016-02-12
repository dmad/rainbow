package damd.rainbow.net.pipeline;

import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Pipeline
{
    private Logger logger;
    private ArrayList<PipelineNode> nodes;
    private boolean nodes_have_been_closed;
    private PipelineState state;

    public Pipeline ()
    {
	logger = Logger.getLogger (getClass ().getName ());
	nodes = new ArrayList<> (3);
	setState (PipelineState.CLOSED);
    }

    public synchronized Pipeline add (final PipelineNode node)
	throws
	    NullPointerException,
	    IllegalArgumentException,
	    IllegalStateException
    {
	if (null == node)
	    throw new NullPointerException ("node");

	if (nodes.contains (node))
	    throw new IllegalArgumentException
		("Node(" + node + ") already in pipeline");

	if (isUsable ())
	    throw new IllegalStateException
		("You can not add a node if the pipeline is in use");

	node.setPipeline (this);
	nodes.add (node);

	return this;
    }

    public synchronized PipelineState getState ()
    {
	return state;
    }

    private void setState (final PipelineState state)
    {
	synchronized (this) {
	    logger.info ("Changing pipeline state from("
			 + this.state
			 + ") to ("
			 + state + ")");

	    this.state = state;
	}

	for (final PipelineNode node : nodes)
	    node.stateHasChanged (state);
    }

    public synchronized boolean isUsable ()
    {
	return state.ordinal () >= PipelineState.OPEN.ordinal ();
    }

    public synchronized void validate ()
	throws IllegalStateException
    {
	switch (state) {
	case OPEN:
	    setState (PipelineState.VALID);
	    break;
	case VALID:
	    // ignore
	    break;
	default:
	    throw new IllegalStateException
		("Can not validate a pipeline that is "
		 + state);
	}
    }

    public synchronized void invalidate (final String message,
					 final Throwable x)
    {
	setState (PipelineState.INVALID);
	logger.log (Level.WARNING, message, x);
    }

    public synchronized void startClosing ()
    {
	if (isUsable ())
	    setState (PipelineState.CLOSING);
    }

    public synchronized void open ()
	throws
	    IllegalStateException
    {
	if (state.ordinal () > PipelineState.CLOSED.ordinal ())
	    throw new IllegalStateException ("Already open");

	// check validity of nodes
	for (int n = 0;n < nodes.size ();++n) {
	    final PipelineNode node = nodes.get (n);
	    final boolean should_source = n < nodes.size () - 1;
	    final boolean should_target = n > 0;
	    final boolean is_source = node instanceof PipelineSource;
	    final boolean is_target = node instanceof PipelineTarget;

	    if (should_source != is_source)
		throw new IllegalStateException
		    ("Node(" + node + ") should "
		     + (should_source ? "be" : "not be")
		     + " a PipelineSource"
		     + ", but "
		     + (is_source ? "is" : "is not"));

	    if (should_target != is_target)
		throw new IllegalStateException
		    ("Node(" + node + ") should "
		     + (should_target ? "be" : "not be")
		     + " a PipelineTarget"
		     + ", but "
		     + (is_target ? "is" : "is not"));
	}

	// link nodes
	for (int n = 0;n < nodes.size () - 1;++n) {
	    final PipelineSource source =
		(PipelineSource) nodes.get (n);
	    final PipelineTarget target =
		(PipelineTarget) nodes.get (n + 1);

	    source.setTarget (target);
	    target.setSource (source);
	}

	try {
	    // open nodes (from initial source to final target)
	    /* phase 0: setup yourself but do not communicate with
	       your sibling node(s)
	       phase 1: do whatever you need to do with your siblings */
	    for (short phase = 0;phase < 2;++phase)
		for (final PipelineNode node : nodes)
		    node.openNode (phase);

	    setState (PipelineState.OPEN);
	    nodes_have_been_closed = false;
	} catch (Throwable x) {
	    for (final PipelineNode node : nodes)
		node.closeNode ();
	}
    }

    public synchronized void close ()
    {
	if (!nodes_have_been_closed) {
	    // from final target to initial source
	    for (int n = nodes.size () - 1;n >= 0;--n)
		nodes.get (n).closeNode ();

	    if (PipelineState.INVALID != state)
		setState (PipelineState.CLOSED);

	    nodes_have_been_closed = true;
	}
    }
}
