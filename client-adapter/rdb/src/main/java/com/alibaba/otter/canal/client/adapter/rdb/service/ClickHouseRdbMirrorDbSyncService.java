package com.alibaba.otter.canal.client.adapter.rdb.service;

import com.alibaba.otter.canal.client.adapter.rdb.config.MirrorDbConfig;

import javax.sql.DataSource;
import java.util.Map;

/**
 * ${DESCRIPTION}
 *
 * @author wuzq
 * @create 2019-12-15 11:21
 **/
public class ClickHouseRdbMirrorDbSyncService extends RdbMirrorDbSyncService {
    public ClickHouseRdbMirrorDbSyncService(Map<String, MirrorDbConfig> mirrorDbConfigCache, DataSource dataSource, Integer threads, Map<String, Map<String, Integer>> columnsTypeCache, boolean skipDupException) {
        super(mirrorDbConfigCache, dataSource, threads, columnsTypeCache, skipDupException);
        rdbSyncService = new ClickHouseRdbSyncService(dataSource, threads, columnsTypeCache, skipDupException);
    }
}
