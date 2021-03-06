package damd.rainbow.sql;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.sql.SQLException;
import java.sql.DriverManager;
import java.sql.Driver;
import java.sql.Connection;
import java.sql.Statement;

import damd.rainbow.behavior.Engine;

import damd.rainbow.util.IterableEnumeration;

import damd.rainbow.conf.ConfigurationException;

public class ConnectionPool
    implements
        Runnable,
        Engine
{
    private static class AvailableConnection
    {
        private Connection conn;
        private long last_used; // in milliseconds (see: System.currentTimeMillis ())

        public AvailableConnection (final Connection conn)
        {
            this.conn = conn;
            last_used = System.currentTimeMillis ();
        }

        public Connection getConnection ()
        {
            return conn;
        }

        public int getIdleTime ()
        {
            return (int) ((System.currentTimeMillis () - last_used) / 1000L);
        }
    }

    private Logger logger;

    private String name;
    private String ex_prefix;

    private Driver driver;
    private String url;
    private Properties properties;

    private int initial_capacity;
    private int maximum_capacity;
    private int capacity_increment;

    private int reclaim_timeout; // in seconds (<= 0 ~ no timeout)
    private int retire_timeout; // in seconds (<= 0 ~ no timeout)

    private List<AvailableConnection> available_connections;
    private List<ConnectionProxy> used_connections;

    private Thread worker;

    public ConnectionPool ()
    {
        setName (null);

        reclaim_timeout = 0; // ~ no timeout
        retire_timeout = 0; // ~ no timeout

        initial_capacity = 1;
        maximum_capacity = Integer.MAX_VALUE;
        capacity_increment = 1;

        available_connections = new ArrayList<> ();
        used_connections = new ArrayList<> ();
    }

    public String toString ()
    {
        return getClass ().getName () + "(name(" + name + "))";
    }

    private synchronized void checkState (State needed_state)
        throws IllegalStateException
    {
        if (needed_state != currentState ())
            throw new IllegalStateException
                (ex_prefix
                 + "This method can only be invoked when the connection pool"
                 + " is in state("
                 + needed_state
                 + ")");
    }

    public synchronized void setDriver (String driver_class_name,
                                        String url,
                                        Properties properties)
        throws ConfigurationException, SQLException
    {
        if (null == url)
            throw new NullPointerException (ex_prefix + "url");

        checkState (State.STOPPED);

        StringBuilder info = new StringBuilder ();
        Driver driver;
        Properties prop_copy;

        info.append ("Finding driver accepting url(");
        info.append (url);
        info.append (")\n");

        if (null != driver_class_name) {
            info.append ("and implemented by class(");
            info.append (driver_class_name);
            info.append (")\n");
        }

        if (null == driver_class_name)
            driver = DriverManager.getDriver (url);
        else
            driver = null;

        for (Driver d
                 : IterableEnumeration.make (DriverManager.getDrivers ())) {
            info.append ('\t');
            if ((null != driver && driver.equals (d))
                || (null != driver_class_name
                    && driver_class_name.equals (d.getClass ().getName ()))) {
                driver = d;
                info.append ('*');
            } else
                info.append ('-');
            info.append (' ');
            info.append (d.getClass ().getName ());
            info.append (" (");
            info.append (d.getMajorVersion ());
            info.append ('.');
            info.append (d.getMinorVersion ());
            info.append (")\n");
        }

        logger.fine (info.toString ());

        if (null == driver)
            throw new ConfigurationException
                (ex_prefix
                 + "No suitable driver could be found for url("
                 + url
                 + ") with class("
                 + driver_class_name
                 + ")");

        if (!driver.acceptsURL (url))
            throw new ConfigurationException
                (ex_prefix
                 + "The driver("
                 + driver.getClass ().getName ()
                 + ") does not accept url("
                 + url
                 + ")");

        if (null == properties)
            prop_copy = null;
        else {
            prop_copy = new Properties (); // make copy

            info.setLength (0);
            info.append ("Properties used by driver:\n");
            for (Map.Entry<Object, Object> e : properties.entrySet ()) {
                prop_copy.put (e.getKey (), e.getValue ());
                info.append ("\t- ");
                info.append (e.getKey ());
                info.append (" = '");
                if ("password".equals (e.getKey ()))
                    info.append ("********");
                else
                    info.append (e.getValue ());
                info.append ("'\n");
            }
            logger.fine (info.toString ());
        }

        // Testing if connection is valid (can take some time)
        Connection conn = driver.connect (url, prop_copy);

        conn.close ();

        this.driver = driver;
        this.url = url;
        this.properties = prop_copy;
    }

    public synchronized int getReclaimTimeout ()
    {
        return reclaim_timeout;
    }

    public synchronized void setReclaimTimeout (final int timeout)
    {
        logger.fine ("Setting reclaim_timeout to ("
                     + timeout
                     + " sec) was ("
                     + reclaim_timeout
                     + " sec)");

        reclaim_timeout = timeout;
    }

    public synchronized int getRetireTimeout ()
    {
        return retire_timeout;
    }

    public synchronized void setRetireTimeout (final int timeout)
    {
        logger.fine ("Setting retire_timeout to ("
                     + timeout
                     + " sec) was ("
                     + retire_timeout
                     + " sec)");

        retire_timeout = timeout;
    }

    public synchronized int getInitialCapacity ()
    {
        return initial_capacity;
    }

    public synchronized int getMaximumCapacity ()
    {
        return maximum_capacity;
    }

    public synchronized int getCapacityIncrement ()
    {
        return capacity_increment;
    }

    public synchronized void setCapacity (int initial,
                                          int maximum,
                                          int increment)
        throws ConfigurationException
    {
        checkState (State.STOPPED);

        if (initial > maximum)
            throw new ConfigurationException
                (ex_prefix
                 + "Initial capacity("
                 + initial
                 + ") must be <= then maximum capacity("
                 + maximum
                 + ")");

        logger.fine ("Setting capacity values to (initial("
                     + initial
                     + ") maximum("
                     + maximum
                     + ") increment("
                     + increment
                     + "))");

        initial_capacity = initial;
        maximum_capacity = maximum;
        capacity_increment = increment;
    }

    // >>> Named

    public synchronized String getName ()
    {
        return name;
    }

    public synchronized void setName (final String name)
    {
        logger = Logger.getLogger (getClass ().getName ()
                                   + ".instance(" + name + ")");

        this.name = name;
        ex_prefix = getClass ().getName ()
            .substring (getClass ().getName ().lastIndexOf ('.') + 1)
            + "("
            + name
            + ") : ";
    }

    // <<< Named

    private synchronized void addConnections (final int count)
        throws SQLException
    {
        logger.info ("Adding "
                     + count
                     + " connection(s) to the connection pool for a total of "
                     + (count + available_connections.size ()
                        + used_connections.size ()));

        for (int i = 0;i < count;++i) {
            final Connection conn = driver.connect (url, properties);

            conn.setAutoCommit (false);
            available_connections.add (new AvailableConnection (conn));
        }
    }

    private void discardConnection (final Connection conn)
    {
        if (null != conn) {
            try {
                conn.close ();
            } catch (Exception x) {
                logger.log (Level.WARNING, "While discarding connection", x);
            }
        }
    }

    private boolean isConnectionValid (final Connection conn)
    {
        boolean valid = false;

        if (null != conn) {
            try {
                valid = conn.isValid (10);
            } catch (SQLException x) {
                logger.log (Level.WARNING,
                            " While testing if a connection is valid",
                            x);
            }
        }

        return valid;
    }

    public synchronized Connection getConnection ()
        throws SQLException
    {
        checkState (State.RUNNING);

        ConnectionProxy proxy = null;

        while (null == proxy) { /* as long as we have connections
                                   (or can make extra) */
            if (available_connections.isEmpty ()) {
                int increment = capacity_increment;
                int allowable = maximum_capacity - used_connections.size ();

                if (0 == allowable)
                    throw new SQLException
                        (ex_prefix
                         + "Connection pool has reached its maximum capacity("
                         + maximum_capacity
                         + ")");

                if (increment > allowable)
                    increment = allowable;

                addConnections (increment);
            } else {
                final Connection conn = available_connections.remove (0)
                    .getConnection ();

                if (isConnectionValid (conn)) {
                    proxy = new ConnectionProxy (this, conn);
                    used_connections.add (proxy);
                } else {
                    logger.warning ("Connection from pool is invalid"
                                    + ", removing it from pool");
                    discardConnection (conn);
                }
            }
        }

        logger.fine ("Leased a connection (used("
                     + used_connections.size ()
                     + ") available("
                     + available_connections.size ()
                     + "))");

        return proxy;
    }

    protected synchronized void releaseConnection (ConnectionProxy proxy)
        throws SQLException
    {
        if (null == proxy)
            throw new NullPointerException ("proxy");

        int index = used_connections.indexOf (proxy);

        if (-1 == index) {
            logger.warning ("Trying to release a unknown ConnectionProxy");
            return;
        }

        Connection conn = proxy.detachConnection ();

        if (null == conn) {
            logger.warning ("Trying to detach a detached ConnectionProxy");
            return;
        }

        for (ConnectionProxy.StatementInfo si : proxy.getOpenStatements ()) {
            StringBuilder sb = new StringBuilder ("Closing leaked statement(");

            sb.append (si.statement);
            sb.append (')');
            if (null != si.caller) {
                sb.append (" created by(");
                sb.append (si.caller);
                sb.append (')');
            }
            logger.warning (sb.toString ());

            try {
                si.statement.close ();
            } catch (SQLException x) {
                logger.log (Level.WARNING,
                            "While closing statement(" + si.statement + ")",
                            x);
            }
        }

        try {
            conn.rollback ();
            available_connections.add (new AvailableConnection (conn));
        } catch (SQLException x) {
            logger.log (Level.WARNING,
                        "While performing rollback on released connection", x);
        }

        used_connections.remove (index);

        logger.fine ("Released a connection (used("
                     + used_connections.size ()
                     + ") available("
                     + available_connections.size ()
                     + "))");
    }

    private synchronized void reclaimUsedConnections ()
    {
        if (reclaim_timeout > 0) {
            List<ConnectionProxy> proxies;

            proxies = new ArrayList<> (used_connections);
            for (ConnectionProxy proxy : proxies) {
                if (proxy.getAge () > reclaim_timeout) {
                    logger.warning ("Connection has timed out ("
                                    + proxy.getAge ()
                                    + " sec), reclaiming it");
                    try {
                        releaseConnection (proxy);
                    } catch (SQLException x) {
                        logger.log (Level.SEVERE,
                                    "While reclaiming timed out connection",
                                    x);
                    }
                }
            }
        }
    }

    private synchronized void retireUnusedConnections ()
    {
        if (retire_timeout > 0) {
            final List<AvailableConnection> conns
                = new ArrayList<> (available_connections);

            for (final AvailableConnection ac : conns) {
                if (ac.getIdleTime () > retire_timeout) {
                    logger.info ("Connection has not been used for("
                                 + ac.getIdleTime ()
                                 + " sec), retiring it");
                    available_connections.remove (ac);
                    discardConnection (ac.getConnection ());
                }
            }
        }
    }

    // >>> Runnable

    public void run ()
    {
        boolean running = true;

        logger.fine ("ConnectionPool worker thread has started");

        while (running) {
            try {
                while (true) {
                    reclaimUsedConnections ();
                    retireUnusedConnections ();
                    Thread.sleep (1000);
                }
            } catch (InterruptedException x) {
                running = false;
            } catch (Exception x) {
                logger.log (Level.SEVERE,
                            "While in ConnectionPool worker thread run loop"
                            + ", continuing execution",
                            x);
            }
        }

        logger.fine ("ConnectionPool worker thread has stopped");
    }

    // <<< Runnable

    // >>> Engine

    public synchronized State currentState ()
    {
        State state = State.STOPPED;

        if (null != worker)
            if (worker.isAlive ())
                state = State.RUNNING;
            else
                worker = null; /* thread terminated by itself
                                  (or never started) !!! */

        return state;
    }

    public synchronized void changeState (State new_state)
        throws SQLException, InterruptedException
    {
        if (currentState () != new_state) {
            switch (new_state) {
            case RUNNING:
                if (null == driver)
                    throw new IllegalStateException
                        (ex_prefix + "No driver defined");

                addConnections (initial_capacity);

                worker = new Thread (this,
                                     getClass ().getName ()
                                     + "("
                                     + name
                                     + ")");
                worker.setDaemon (true);
                worker.start ();
                break;
            case STOPPED:
                while (null != worker && worker.isAlive ()) {
                    worker.interrupt ();
                    Thread.sleep (1000);
                }
                worker = null;

                // Reclaim all used connections
                for (ConnectionProxy p : used_connections) {
                    final Connection conn = p.detachConnection ();

                    if (null != conn)
                        available_connections.add
                            (new AvailableConnection (conn));
                }
                used_connections.clear ();

                for (final AvailableConnection ac : available_connections)
                    discardConnection (ac.getConnection ());

                available_connections.clear ();

                break;
            }
        }
    }

    // <<< Engine
}
