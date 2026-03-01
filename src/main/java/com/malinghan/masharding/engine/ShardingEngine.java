package com.malinghan.masharding.engine;

import com.malinghan.masharding.context.ShardingResult;

public interface ShardingEngine {

    /**
     * 执行分片计算
     *
     * @param sql  逻辑 SQL（含逻辑表名）
     * @param args MyBatis 传入的参数数组
     * @return 分片结果（目标数据源 + 物理 SQL）
     */
    ShardingResult sharding(String sql, Object[] args);
}
