package damd.rainbow.sql;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import java.util.concurrent.Executor;

import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.CallableStatement;
import java.sql.DatabaseMetaData;
import java.sql.Savepoint;

import java.sql.SQLClientInfoException;
import java.sql.Clob;
import java.sql.Blob;
import java.sql.NClob;
import java.sql.SQLXML;
import java.sql.Array;
import java.sql.Struct;

import java.sql.SQLNonTransientConnectionException;

public class ConnectionProxy
    implements
        Connection
{
    public static class StatementInfo
    {
        public Statement statement;
        public StackTraceElement caller;
    }

    private ConnectionPool pool;
    private Connection connection;
    private long birth; // in milliseconds (see: System.currentTimeMillis ())
    private Vector<StatementInfo> statements;

    public ConnectionProxy (ConnectionPool pool, Connection connection)
    {
        if (null == pool)
            throw new NullPointerException ("pool");

        if (null == connection)
            throw new NullPointerException ("connection");

        this.pool = pool;
        this.connection = connection;
        this.birth = System.currentTimeMillis ();
        this.statements = new Vector<StatementInfo> ();
    }

    public synchronized int getAge ()
    {
        return (int) ((System.currentTimeMillis () - birth) / 1000L);
    }

    // may only be called by ConnectionPool.*
    protected synchronized Connection detachConnection ()
    {
        Connection conn = connection;

        connection = null;

        return conn;
    }

    // may only be called by ConnectionPool.*
    protected synchronized List<StatementInfo> getOpenStatements ()
    {
        Vector<StatementInfo> open = new Vector<StatementInfo> ();

        for (StatementInfo si : statements) {
            try {
                if (!(si.statement.isClosed ()))
                    open.add (si);
            } catch (SQLException x) {
                // isClosed is acting funny, add it to our list
                open.add (si);
            }
        }

        return open;
    }

    private synchronized void trackStatement (Statement stmt)
    {
        if (null != stmt) {
            StatementInfo si = new StatementInfo ();
            StackTraceElement[] stack_trace;

            si.statement = stmt;

            stack_trace = Thread.currentThread ().getStackTrace ();
            /* 0 = Thread.getStackTrace ()
               1 = us (~ trackStatement)
               2 = ConnectionProxy.xxxStatement
               3 = caller
            */
            if (null != stack_trace && stack_trace.length > 3)
                si.caller = stack_trace[3];

            statements.add (si);
        }
    }

    private synchronized void throwIfClosed ()
        throws SQLNonTransientConnectionException
    {
        if (null == connection)
            throw new SQLNonTransientConnectionException
                ("Unsupported operation on closed connection");
    }

    private synchronized void throwIfClosedAsSQLClientInfoException ()
        throws SQLClientInfoException
    {
        try {
            throwIfClosed ();
        } catch (SQLNonTransientConnectionException x) {
            SQLClientInfoException scix = new SQLClientInfoException ();

            scix.initCause (x);
            throw scix;
        }
    }

    // >>> Connection

    public void close ()
        throws SQLException
    {
        /* if ConnectionPool monitor and ConnectionProxy monitor need
           to be locked in the same thread
           => first ConnectionPool then ConnectionProxy
           , that's why close MAY NOT be synchronized !!! */
        pool.releaseConnection (this);
    }

    public synchronized boolean isClosed ()
        throws SQLException
    {
        return null == connection;
    }

    public synchronized Statement createStatement ()
        throws SQLException
    {
        throwIfClosed ();

        Statement stmt = connection.createStatement ();

        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement (String sql)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt = connection.prepareStatement (sql);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized CallableStatement prepareCall (String sql)
        throws SQLException
    {
        throwIfClosed ();

        CallableStatement stmt = connection.prepareCall (sql);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized Statement createStatement (int resultSetType,
                                                   int resultSetConcurrency)
        throws SQLException
    {
        throwIfClosed ();

        Statement stmt = connection.createStatement (resultSetType,
                                                     resultSetConcurrency);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement
        (String sql,
         int resultSetType,
         int resultSetConcurrency)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt;

        stmt = connection.prepareStatement (sql,
                                            resultSetType,
                                            resultSetConcurrency);
        trackStatement (stmt);

        return stmt;
    }

    public synchronized CallableStatement prepareCall (String sql,
                                                       int resultSetType,
                                                       int resultSetConcurrency)
        throws SQLException
    {
        throwIfClosed ();

        CallableStatement stmt = connection.prepareCall (sql,
                                                         resultSetType,
                                                         resultSetConcurrency);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized Statement createStatement (int resultSetType,
                                                   int resultSetConcurrency,
                                                   int resultSetHoldability)
        throws SQLException
    {
        throwIfClosed ();

        Statement stmt = connection.createStatement (resultSetType,
                                                     resultSetConcurrency,
                                                     resultSetHoldability);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement
        (String sql,
         int resultSetType,
         int resultSetConcurrency,
         int resultSetHoldability)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt;

        stmt = connection.prepareStatement (sql, resultSetType,
                                            resultSetConcurrency,
                                            resultSetHoldability);
        trackStatement (stmt);

        return stmt;
    }

    public synchronized CallableStatement prepareCall (String sql,
                                                       int resultSetType,
                                                       int resultSetConcurrency,
                                                       int resultSetHoldability)
        throws SQLException
    {
        throwIfClosed ();

        CallableStatement stmt = connection.prepareCall (sql, resultSetType,
                                                         resultSetConcurrency,
                                                         resultSetHoldability);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement
        (String sql,
         int autoGeneratedKeys)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt;

        stmt = connection.prepareStatement (sql,
                                            autoGeneratedKeys);
        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement (String sql,
                                                            int[] columnIndexes)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt = connection.prepareStatement (sql,
                                                              columnIndexes);

        trackStatement (stmt);

        return stmt;
    }

    public synchronized PreparedStatement prepareStatement
        (String sql,
         String[] columnNames)
        throws SQLException
    {
        throwIfClosed ();

        PreparedStatement stmt = connection.prepareStatement (sql, columnNames);

        trackStatement (stmt);

        return stmt;
    }

    // Forwarders

    public synchronized void setAutoCommit (boolean autoCommit)
        throws SQLException
    {
        throwIfClosed ();

        connection.setAutoCommit (autoCommit);
    }

    public synchronized String nativeSQL (String sql)
        throws SQLException
    {
        throwIfClosed ();

        return connection.nativeSQL (sql);
    }

    public synchronized boolean getAutoCommit ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getAutoCommit ();
    }

    public synchronized void commit ()
        throws SQLException
    {
        throwIfClosed ();

        connection.commit ();
    }

    public synchronized void rollback ()
        throws SQLException
    {
        throwIfClosed ();

        connection.rollback ();
    }

    public synchronized DatabaseMetaData getMetaData ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getMetaData ();
    }

    public synchronized void setReadOnly (boolean readOnly)
        throws SQLException
    {
        throwIfClosed ();

        connection.setReadOnly (readOnly);
    }

    public synchronized boolean isReadOnly ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.isReadOnly ();
    }

    public synchronized void setCatalog (String catalog)
        throws SQLException
    {
        throwIfClosed ();

        connection.setCatalog (catalog);
    }

    public synchronized String getCatalog ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getCatalog ();
    }

    public synchronized void setTransactionIsolation (int level)
        throws SQLException
    {
        throwIfClosed ();

        connection.setTransactionIsolation (level);
    }

    public synchronized int getTransactionIsolation ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getTransactionIsolation ();
    }

    public synchronized SQLWarning getWarnings ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getWarnings ();
    }

    public synchronized void clearWarnings ()
        throws SQLException
    {
        throwIfClosed ();

        connection.clearWarnings ();
    }

    public synchronized Map<String, Class<?>> getTypeMap ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getTypeMap ();
    }

    public synchronized void setTypeMap (Map<String, Class<?>> map)
        throws SQLException
    {
        throwIfClosed ();

        connection.setTypeMap (map);
    }

    public synchronized void setHoldability (int holdability)
        throws SQLException
    {
        throwIfClosed ();

        connection.setHoldability (holdability);
    }

    public synchronized int getHoldability ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getHoldability ();
    }

    public synchronized Savepoint setSavepoint ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.setSavepoint ();
    }

    public synchronized Savepoint setSavepoint (String name)
        throws SQLException
    {
        throwIfClosed ();

        return connection.setSavepoint (name);
    }

    public synchronized void rollback (Savepoint savepoint)
        throws SQLException
    {
        throwIfClosed ();

        connection.rollback (savepoint);
    }

    public synchronized void releaseSavepoint (Savepoint savepoint)
        throws SQLException
    {
        throwIfClosed ();

        connection.releaseSavepoint (savepoint);
    }

    // Since: 1.6

    public synchronized Clob createClob ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.createClob ();
    }

    public synchronized Blob createBlob ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.createBlob ();
    }

    public synchronized NClob createNClob ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.createNClob ();
    }

    public synchronized SQLXML createSQLXML ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.createSQLXML ();
    }

    public synchronized boolean isValid (int timeout)
        throws SQLException
    {
        return null != connection && connection.isValid (timeout);
    }

    public synchronized void setClientInfo (String name,
                                            String value)
        throws SQLClientInfoException
    {
        throwIfClosedAsSQLClientInfoException ();

        connection.setClientInfo (name, value);
    }

    public synchronized void setClientInfo (Properties properties)
        throws SQLClientInfoException
    {
        throwIfClosedAsSQLClientInfoException ();

        connection.setClientInfo (properties);
    }

    public synchronized String getClientInfo (String name)
        throws SQLException
    {
        throwIfClosed ();

        return connection.getClientInfo (name);
    }

    public synchronized Properties getClientInfo ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getClientInfo ();
    }

    public synchronized Array createArrayOf (String typeName,
                                             Object[] elements)
        throws SQLException
    {
        throwIfClosed ();

        return connection.createArrayOf (typeName, elements);
    }

    public synchronized Struct createStruct (String typeName,
                                             Object[] attributes)
        throws SQLException
    {
        throwIfClosed ();

        return connection.createStruct (typeName, attributes);
    }

    // >>> Connection >>> Wrapper (since 1.6)

    public synchronized boolean isWrapperFor (Class<?> iface)
        throws SQLException
    {
        throwIfClosed ();

        return connection.isWrapperFor (iface);
    }

    public synchronized <T> T unwrap (Class<T> iface)
        throws SQLException
    {
        throwIfClosed ();

        return connection.unwrap (iface);
    }

    // <<< Connection >>> Wrapper

    // Since 1.7

    public synchronized void setSchema (String schema)
        throws SQLException
    {
        throwIfClosed ();

        connection.setSchema (schema);
    }

    public synchronized String getSchema ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getSchema ();
    }

    public synchronized void abort (Executor executor)
        throws SQLException
    {
        throwIfClosed ();

        connection.abort (executor);
    }

    public synchronized void setNetworkTimeout (Executor executor,
                                   int milliseconds)
        throws SQLException
    {
        throwIfClosed ();

        connection.setNetworkTimeout (executor, milliseconds);
    }

    public synchronized int getNetworkTimeout ()
        throws SQLException
    {
        throwIfClosed ();

        return connection.getNetworkTimeout ();
    }

    // <<< Connection
}
