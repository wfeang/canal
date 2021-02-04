package com.alibaba.otter.canal.client.adapter.rdb.service;

import com.alibaba.otter.canal.client.adapter.rdb.config.MappingConfig;
import com.alibaba.otter.canal.client.adapter.rdb.support.SyncUtil;
import com.alibaba.otter.canal.client.adapter.support.AdapterConfig;
import com.alibaba.otter.canal.client.adapter.support.Util;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author wfeang on 2021-01-05 15:42
 */
public class ClickHouseRdbEtlService extends RdbEtlService {

    private DataSource targetDS;
    private MappingConfig config;

    public ClickHouseRdbEtlService(DataSource targetDS, MappingConfig config) {
        super(targetDS, config);
        this.targetDS = targetDS;
        this.config = config;
    }

    /**
     * 拼接目标表主键where条件
     */
    protected static String getCondition(MappingConfig.DbMapping dbMapping, Map<String, String> values) throws SQLException {
        StringBuilder deleteParams = new StringBuilder();
        // 拼接主键
        for (Map.Entry<String, String> entry : dbMapping.getTargetPk().entrySet()) {
            String targetColumnName = entry.getKey();
            String srcColumnName = entry.getValue();
            if (srcColumnName == null) {
                srcColumnName = targetColumnName;
            }
            deleteParams.append(targetColumnName).append("=? AND ");
            values.put(targetColumnName, srcColumnName);
        }
        int len = deleteParams.length();
        deleteParams.delete(len - 4, len);
        return deleteParams.toString();
    }

    protected boolean executeSqlImport(DataSource srcDS, String sql, List<Object> values,
                                       AdapterConfig.AdapterMapping mapping, AtomicLong impCount, List<String> errMsg) {
        try {
            MappingConfig.DbMapping dbMapping = (MappingConfig.DbMapping) mapping;
            Map<String, String> columnsMap = new LinkedHashMap<>();
            Map<String, Integer> columnType = new LinkedHashMap<>();

            Util.sqlRS(targetDS, "SELECT * FROM " + SyncUtil.getDbTableName(dbMapping) + " LIMIT 1 ", rs -> {
                try {

                    ResultSetMetaData rsd = rs.getMetaData();
                    int columnCount = rsd.getColumnCount();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        columnType.put(rsd.getColumnName(i).toLowerCase(), rsd.getColumnType(i));
                        columns.add(rsd.getColumnName(i));
                    }

                    columnsMap.putAll(SyncUtil.getColumnsMap(dbMapping, columns));
                    return true;
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    return false;
                }
            });

            int batchSize = dbMapping.getCommitBatch();
            Util.sqlRS(srcDS, sql, values, rs -> {
                int idx = 0;

                try {

                    boolean excute = false;

//                    String deletePrex = "alter table " + SyncUtil.getDbTableName(dbMapping) + " delete where ";
//                    Map<String, String> keyMapping = new HashMap<String, String>();
//                    String deleteCondition = getCondition(dbMapping, keyMapping);
//                    List<Object> deleteParam = new ArrayList<>();


                    StringBuilder insertSql = new StringBuilder();
                    insertSql.append("INSERT INTO ").append(SyncUtil.getDbTableName(dbMapping)).append(" (");
                    columnsMap.forEach((targetColumnName, srcColumnName) -> insertSql.append(targetColumnName).append(","));

                    int len = insertSql.length();
                    insertSql.delete(len - 1, len).append(") VALUES (");
                    int mapLen = columnsMap.size();
                    for (int i = 0; i < mapLen; i++) {
                        insertSql.append("?,");
                    }
                    len = insertSql.length();
                    insertSql.delete(len - 1, len).append(")");
                    try (Connection connTarget = targetDS.getConnection();
                         PreparedStatement pstmt = connTarget.prepareStatement(insertSql.toString())) {
                        connTarget.setAutoCommit(true);
                        while (rs.next()) {
                            int i = 1;


                            for (Map.Entry<String, String> entry : columnsMap.entrySet()) {
                                String targetClolumnName = entry.getKey();
                                String srcColumnName = entry.getValue();
                                if (srcColumnName == null) {
                                    srcColumnName = targetClolumnName;
                                }

                                Integer type = columnType.get(targetClolumnName.toLowerCase());

                                Object value = rs.getObject(srcColumnName);
                                if (value != null) {
                                    SyncUtil.setPStmt(type, pstmt, value, i);
                                } else {
                                    pstmt.setNull(i, type);
                                }

                                i++;
                            }


                            pstmt.addBatch();
                            idx++;
                            if (idx % batchSize != 0) {
                                excute = false;
                                continue;
                            }
//                            for (Map.Entry<String, String> stringStringEntry : keyMapping.entrySet()) {
//                                deleteParam.add(rs.getObject(stringStringEntry.getValue()));
//                            }
//                            StringBuilder dele = new StringBuilder(deletePrex);
//                            for (int i1 = 0; i1 < idx; i1++) {
//                                dele.append("(").append(deleteCondition).append(") or ");
//                            }
//                            dele.delete(dele.length() - 3, dele.length());
//                            try (PreparedStatement dpstmt = connTarget.prepareStatement(dele.toString())) {
//                                for (int i1 = 0; i1 < deleteParam.size(); i1++) {
//                                    dpstmt.setObject(i1 + 1, deleteParam.get(i1));
//                                }
//                                dpstmt.execute();
//                            }
//                            deleteParam.clear();
                            excute = true;
                            pstmt.executeBatch();
                            pstmt.clearBatch();
                            pstmt.clearParameters();

                            for (int i1 = 0; i1 < idx; i1++) {
                                impCount.incrementAndGet();
                            }
                            idx = 0;
                            if (logger.isTraceEnabled()) {
                                logger.trace("Insert into target table, sql: {}", insertSql);
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("successful import count:" + impCount.get());
                            }
                        }
                        if (!excute) {

                            for (int i1 = 0; i1 < idx; i1++) {
                                impCount.incrementAndGet();
                            }
//                            StringBuilder dele = new StringBuilder(deletePrex);
//                            for (int i1 = 0; i1 < idx; i1++) {
//                                dele.append("(").append(deleteCondition).append(") or ");
//                            }
//                            dele.delete(dele.length() - 3, dele.length());
//                            try (PreparedStatement dpstmt = connTarget.prepareStatement(dele.toString())) {
//                                for (int i1 = 0; i1 < deleteParam.size(); i1++) {
//                                    dpstmt.setObject(i1 + 1, deleteParam.get(i1));
//                                }
//                                dpstmt.execute();
//                            }
                            pstmt.executeBatch();
                            pstmt.clearParameters();
                            pstmt.clearBatch();
                            if (logger.isTraceEnabled()) {
                                logger.trace("Insert into target table, sql: {}", insertSql);
                            }
                            if (logger.isDebugEnabled()) {
                                logger.debug("successful import count:" + impCount.get());
                            }
                        }
                    }


                } catch (Exception e) {
                    logger.error(dbMapping.getTable() + " etl failed! ==>" + e.getMessage(), e);
                    errMsg.add(dbMapping.getTable() + " etl failed! ==>" + e.getMessage());
                }
                return idx;
            });
            return true;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }
}
