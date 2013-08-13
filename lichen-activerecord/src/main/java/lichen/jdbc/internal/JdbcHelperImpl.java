package lichen.jdbc.internal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import lichen.core.services.LichenException;
import lichen.jdbc.services.JdbcErrorCode;
import lichen.jdbc.services.JdbcHelper;
import lichen.jdbc.services.PreparedStatementSetter;
import lichen.jdbc.services.ResultSetCallback;
import lichen.jdbc.services.ResultSetGetter;
import lichen.jdbc.services.RowMapper;

/**
 * 实现JdbcHelper,此类非线程安全，只能在某一个线程中运行.
 * @author jcai
 */
public class JdbcHelperImpl implements JdbcHelper {
    //操作的底层数据库
    private final DataSource dataSource;
    //当前线程绑定的事务
    private final ThreadLocal<Transaction> currentTransaction;
    public JdbcHelperImpl(final DataSource vdataSource) {
        this.dataSource = vdataSource;
        currentTransaction = new ThreadLocal<Transaction>();
    }
    //事务定义
    private static class Transaction {
        Connection connection;
        boolean autoCommit;
        int hold;

        Transaction(final Connection vconnection, final boolean vautoCommit) {
            this.connection = vconnection;
            this.autoCommit = vautoCommit;
        }
    }

    @Override
    public final void holdConnection() {
        Transaction transaction = currentTransaction.get();

        try {
            if (transaction == null) {
                transaction = new Transaction(dataSource.getConnection(), true);
            }

            transaction.hold++;

            currentTransaction.set(transaction);
        } catch (SQLException e) {
            throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
        }
    }

    @Override
    public final void releaseConnection() {
        Transaction transaction = currentTransaction.get();
        if (transaction == null) {
            throw new RuntimeException("There isn't a current "
                    + "connection to release");
        }

        transaction.hold--;

        if (transaction.hold == 0) {
            if (!transaction.autoCommit) {
                try {
                    transaction.connection.commit();
                    transaction.connection.setAutoCommit(true);
                } catch (SQLException e) {
                    throw LichenException.wrap(e,
                            JdbcErrorCode.DATA_ACCESS_ERROR);
                } finally {
                    JdbcUtil.close(transaction.connection);
                    currentTransaction.remove();
                }
            } else {
                JdbcUtil.close(transaction.connection);
                currentTransaction.remove();
            }
        }
    }

    @Override
    public final void beginTransaction() {
        Transaction transaction = currentTransaction.get();
        try {
            if (transaction == null) {
                transaction = new Transaction(dataSource.getConnection(),
                        false);
            } else {
                transaction.autoCommit = false;
            }
            transaction.connection.setAutoCommit(false);

            transaction.hold++;

            currentTransaction.set(transaction);
        } catch (SQLException e) {
            throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
        }
    }

    @Override
    public final void commitTransaction() {
        Transaction transaction = currentTransaction.get();

        if (transaction == null || transaction.autoCommit) {
            throw new LichenException(
                    "There isn't a current transaction to comit",
                    JdbcErrorCode.NO_TRANSACTION_IN_CURRENT_THREAD);
        } else {
            try {
                transaction.connection.commit();
                transaction.connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
            } finally {
                JdbcUtil.close(transaction.connection);
                currentTransaction.remove();
            }
        }
    }
    private Connection getConnection() throws SQLException {
        Transaction transaction = currentTransaction.get();

        if (transaction == null) {
            return dataSource.getConnection();
        } else {
            return transaction.connection;
        }
    }
    private boolean isConnectionHeld() {
        return currentTransaction.get() != null;
    }
    private boolean isInTransaction() {
        Transaction transaction = currentTransaction.get();
        return transaction != null && !transaction.autoCommit;
    }

    @Override
    public final int execute(final String sql, final Object ... params) {
        Connection conn = null;
        PreparedStatement ps = null;
        boolean rollBack = false;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            int index = 0;
            for (Object obj : params) {
                ps.setObject(++index, obj);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            rollBack = isInTransaction();
            throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
        } finally {
            JdbcUtil.close(ps);
            if (rollBack) {
                rollbackTransaction();
            } else {
                freeConnection(conn);
            }
        }
    }

    @Override
    public final <T> List<T> queryForList(final String sql,
            final RowMapper<T> mapper) {
        return internalQueryForList(sql, mapper);
    }

    @Override
    public final <T> List<T> queryForList(final String sql,
            final RowMapper<T> mapper,
            final PreparedStatementSetter... setters) {
        return internalQueryForList(sql, mapper, setters);
    }
    private <T> List<T> internalQueryForList(final String sql,
            final RowMapper<T> mapper,
            final PreparedStatementSetter... setters) {
        Connection conn = null;
        PreparedStatement ps = null;
        List<T> list = new ArrayList<T>();
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            int index = 1;
            for (PreparedStatementSetter setter : setters) {
                setter.set(ps, index);
                index++;
            }
            ResultSet rs = ps.executeQuery();
            index = 0;
            while (rs.next()) {
                list.add(mapper.mapRow(rs, index));
                index++;
            }
            return list;
        } catch (SQLException e) {
            throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
        } finally {
            JdbcUtil.close(ps);
            freeConnection(conn);
        }
    }

    @Override
    public final <T> T queryForFirst(final String sql,
    		final ResultSetGetter<T> getter,
    	    final PreparedStatementSetter... setters) {
        return withResultSet(sql, new ResultSetCallback<T>() {
            @Override
            public T doInResultSet(final ResultSet rs)
            throws SQLException {
                if (rs.next()) {
                    return getter.get(rs, 0);
                }
                return null;
            } } , setters);
    }

    private void freeConnection(final Connection con) {
        if (!isConnectionHeld()) {
            JdbcUtil.close(con);
        }
    }
    private void rollbackTransaction() {
        Transaction transaction = currentTransaction.get();

        if (transaction == null || transaction.autoCommit) {
            throw new RuntimeException(
            		"There isn't a current transaction to rollback");
        } else {
            try {
                transaction.connection.rollback();
                transaction.connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
            } finally {
                JdbcUtil.close(transaction.connection);
                currentTransaction.remove();
            }
        }
    }
    @Override
    public final <T> T withResultSet(final String sql,
    		final ResultSetCallback<T> callback) {
        return internalWithResultSet(sql, callback);
    }

    @Override
    public final <T> T withResultSet(final String sql,
    		final ResultSetCallback<T> callback,
            final PreparedStatementSetter... setters) {
        return internalWithResultSet(sql, callback, setters);
    }

    private <T> T internalWithResultSet(final String sql,
    		final ResultSetCallback<T> callback,
            final PreparedStatementSetter... setters) {
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            int index = 1;
            for (PreparedStatementSetter setter : setters) {
                setter.set(ps, index);
                index++;
            }
            ResultSet rs = ps.executeQuery();
            return callback.doInResultSet(rs);
        } catch (SQLException e) {
            throw LichenException.wrap(e, JdbcErrorCode.DATA_ACCESS_ERROR);
        } finally {
            JdbcUtil.close(ps);
            freeConnection(conn);
        }
    }
}
