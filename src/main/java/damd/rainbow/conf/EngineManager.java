package damd.rainbow.conf;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;

import java.util.logging.Logger;

import damd.rainbow.behavior.Named;
import damd.rainbow.behavior.Engine;

public class EngineManager
    implements
	Named
{
    private static class EngineMeta
    {
	public Engine.State initial_state;
	public Engine.State set_state;
	public Vector<Engine> dependencies;
	public Vector<Engine> dependents;

	public EngineMeta (Engine.State initial_state)
	{
	    this.initial_state = initial_state;
	}

	public void addDependency (Engine dependency)
	{
	    if (null == dependency)
		throw new NullPointerException ("dependency");

	    if (null != dependencies && dependencies.contains (dependency))
		return; // already present

	    if (null == dependencies)
		dependencies = new Vector<Engine> ();

	    dependencies.add (dependency);
	}

	public void addDependent (Engine dependent)
	{
	    if (null == dependent)
		throw new NullPointerException ("dependent");

	    if (null != dependents && dependents.contains (dependent))
		return; // already present

	    if (null == dependents)
		dependents = new Vector<Engine> ();

	    dependents.add (dependent);
	}
    }

    private Logger logger;

    private String name;

    private Hashtable<Engine, EngineMeta> engines;
    private Hashtable<String, Engine> engine_names;

    public EngineManager ()
    {
	logger = Logger.getLogger (getClass ().getName ());

	engines = new Hashtable<Engine, EngineMeta> ();
	engine_names = new Hashtable<String, Engine> ();
    }

    public synchronized void clear ()
    {
	engines.clear ();
	engine_names.clear ();
    }

    public synchronized boolean contains (Engine engine)
    {
	return engines.containsKey (engine);
    }

    public synchronized Engine get (final String engine_name)
    {
	return engine_names.get (engine_name);
    }

    private synchronized void addEngine (Engine engine, EngineMeta meta)
    {
	if (null == engine)
	    throw new NullPointerException ("engine");

	if (null == meta)
	    throw new NullPointerException ("meta");

	if (engines.containsKey (engine))
	    throw new IllegalArgumentException ("Engine("
						+ engine
						+ ") already exists");

	if (engine_names.containsKey (engine.getName ()))
	    throw new IllegalArgumentException ("Engine name("
						+ engine.getName ()
						+ ") already exits");

	engines.put (engine, meta);
	engine_names.put (engine.getName (), engine);
    }

    public synchronized void addEngine (Engine engine,
					Engine.State initial_state)
    {
	if (null == engine)
	    throw new NullPointerException ("engine");

	if (null == initial_state)
	    throw new NullPointerException ("initial_state");

	addEngine (engine, new EngineMeta (initial_state));
    }

    public synchronized List<Engine> getDependencies (Engine engine)
    {
	if (null == engine)
	    throw new NullPointerException ("engine");

	Vector<Engine> deps = new Vector<Engine> ();
	EngineMeta meta = engines.get (engine);

	if (null != meta && null != meta.dependencies)
	    deps.addAll (meta.dependencies);

	return deps;
    }

    public synchronized void addDependency (Engine from, Engine to)
    {
	if (!(contains (from)))
	    throw new IllegalArgumentException ("from("
						+ from
						+ ") is unknown");

	if (!(contains (to)))
	    throw new IllegalArgumentException ("to("
						+ to
						+ ") is unknown");

	engines.get (from).addDependency (to);
	engines.get (to).addDependent (from);
    }

    public synchronized List<Engine> createRunList ()
    {
	Vector<Engine> unresolved = new Vector<Engine> (engines.keySet ());
	Vector<Engine> resolved = new Vector<Engine> ();
	int resolved_count;

	do {
	    Iterator<Engine> i = unresolved.iterator ();
	    resolved_count = 0;

	    while (i.hasNext ()) {
		Engine e = i.next ();
		EngineMeta m = engines.get (e);
		int deps = null == m.dependencies ? 0 : m.dependencies.size ();
		int resolved_deps = 0;

		if (deps > 0)
		    for (Engine d : m.dependencies)
			if (resolved.contains (d))
			    ++resolved_deps;

		if (resolved_deps == deps) {
		    resolved.add (e);
		    i.remove ();
		    ++resolved_count;
		}
	    }
	} while (!(unresolved.isEmpty ()) && resolved_count > 0);

	if (!(unresolved.isEmpty ())) {
	    StringBuilder sb = new StringBuilder ();
	    Iterator<Engine> i = unresolved.iterator ();

	    sb.append ("Not all engines could be added to the run list(");
	    while (i.hasNext ()) {
		sb
		    .append ("'")
		    .append (i.next ().getName ())
		    .append ("'");

		if (i.hasNext ())
		    sb.append (", ");
	    }
	    sb.append (')');

	    throw new IllegalStateException (sb.toString ());
	}

	return resolved;
    }

    public synchronized List<Engine> createStateChangeList
	(Engine engine, Engine.State new_state)
    {
	Vector<Engine> list = new Vector<Engine> ();
	EngineMeta meta;

	switch (new_state) {
	case RUNNING:
	    // all its dependencies need to be running before it starts
	    meta = engines.get (engine);
	    if (null != meta && null != meta.dependencies)
		for (Engine dep : meta.dependencies)
		    list.addAll (createStateChangeList (dep, new_state));
	    list.add (engine);
	    break;
	case STOPPED:
	    // all its dependents need to be stopped before it stops
	    meta = engines.get (engine);
	    if (null != meta && null != meta.dependents)
		for (Engine dep : meta.dependents)
		    list.addAll (createStateChangeList (dep, new_state));
	    list.add (engine);
	    break;
	}

	return list;
    }

    public synchronized void changeState (Engine engine, Engine.State new_state)
	throws Exception
    {
	if (null == engine)
	    throw new NullPointerException ("engine");

	if (null == new_state)
	    throw new NullPointerException ("new_state");

	if (!(contains (engine)))
	    throw new IllegalArgumentException ("Engine("
						+ engine.getName ()
						+ ") is unknown");

	List<Engine> list = createStateChangeList (engine, new_state);

	{
	    StringBuilder log;
	    int change_count = 0;

	    log = new StringBuilder ("Changing following engines to ")
		.append (new_state)
		.append (" (");

	    for (Engine e : list)
		if (e.currentState () != new_state) {
		    if (0 != change_count)
			log.append (", ");
		    log.append (e.getName ());
		    ++change_count;
		}

	    if (change_count > 0) {
		log.append (")");
		logger.info (log.toString ());
	    }
	}

	for (Engine e : list)
	    if (e.currentState () != new_state) {
		EngineMeta m = engines.get (e);

		e.changeState (new_state);
		m.set_state = new_state;
	    }
    }

    public synchronized void changeAllToInitialState ()
	throws Exception
    {
	List<Engine> run_list = createRunList ();

	{
	    StringBuilder log;

	    log = new StringBuilder
		("Preparing all engines to change to their initial state:");

	    for (Engine e : run_list) {
		EngineMeta m = engines.get (e);

		log
		    .append ("\n\t- ")
		    .append (e.getName ());

		if (e.currentState () != m.initial_state)
		    log
			.append (" ")
			.append (e.currentState ())
			.append (" => ")
			.append (m.initial_state);

		else
		    log
			.append (" (is already in its initial ")
			.append (m.initial_state)
			.append (" state)");
	    }

	    logger.info (log.toString ());
	}

	for (Engine e : run_list) {
	    EngineMeta m = engines.get (e);

	    if (e.currentState () != m.initial_state)
		changeState (e, m.initial_state);
	}
    }

    public synchronized void stopAll ()
	throws Exception
    {
	List<Engine> run_list = createRunList ();

	Collections.reverse (run_list);

	{
	    StringBuilder log;

	    log = new StringBuilder
		("Preparing all engines to be stopped:");

	    for (Engine e : run_list) {
		log
		    .append ("\n\t- ")
		    .append (e.getName ());

		if (e.currentState () == Engine.State.STOPPED)
		    log.append (" already stopped");
	    }

	    logger.info (log.toString ());
	}

	for (Engine e : run_list) {
	    EngineMeta m = engines.get (e);

	    if (e.currentState () != Engine.State.STOPPED)
		changeState (e, Engine.State.STOPPED);
	}
    }

    // >>> Named

    public String getName ()
    {
	return name;
    }

    public void setName (final String name)
    {
	if (!((null != name ? name : "").equals (this.name))) {
	    this.name = name;

	    logger = Logger.getLogger (getClass ().getName ()
				       + ".instance(" + name + ")");
	}
    }

    // <<< Named
}
