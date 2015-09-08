package damd.rainbow.net.pipeline;

import java.util.ArrayList;

public class Pipeline
{
    private ArrayList<PipelineNode> nodes;
    private boolean is_open;
    private PipelineState state;

    public Pipeline ()
    {
	nodes = new ArrayList<> (3);
	state = PipelineState.CLOSED;
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

	if (isOpen ())
	    throw new IllegalStateException
		("You can not add a node if the pipeline is open");

	node.setPipeline (this);
	nodes.add (node);

	return this;
    }

    public synchronized boolean isOpen ()
    {
	return state.ordinal () > PipelineState.CLOSED.ordinal ();
    }

    public synchronized void open ()
	throws
	    IllegalStateException
    {
	if (isOpen ())
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
		for (int n = 0;n < nodes.size ();++n)
		    nodes.get (n).openNode (phase);

	    state = PipelineState.VALID;
	} catch (Throwable x) {
	    for (int n = 0;n < nodes.size ();++n)
		nodes.get (n).closeNode ();
	}
    }

    public synchronized void close ()
    {
	if (isOpen ()) {
	    // from final target to initial source
	    for (int n = nodes.size () - 1;n >= 0;--n)
		nodes.get (n).closeNode ();

	    state = PipelineState.CLOSED;
	}
    }

    public synchronized PipelineState getState ()
    {
	return state;
    }

    public synchronized void setState (final PipelineState state)
    {
	this.state = state;
    }
}
