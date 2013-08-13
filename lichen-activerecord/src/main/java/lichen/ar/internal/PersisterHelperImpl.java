package lichen.ar.internal;

import lichen.ar.model.Field;
import lichen.jdbc.services.JdbcHelper;

import java.util.Map;

/**
 * 针对数据库的操作类.
 * @author jcai
 */
public class PersisterHelperImpl {
    //数据库操作类
    private final DatabaseAdapter databaseAdapter;
    PersisterHelperImpl(final DatabaseAdapter vdatabaseAdapter) {
        this.databaseAdapter = vdatabaseAdapter;
    }
    final Map<String, Field<?>> findTableMapping(final String tableName) {
        @SuppressWarnings("unused")
        JdbcHelper helper = databaseAdapter.createJdbcHelper();
        return null;
    }
}
