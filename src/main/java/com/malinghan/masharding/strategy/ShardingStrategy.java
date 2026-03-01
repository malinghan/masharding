package com.malinghan.masharding.strategy;

import java.util.List;
import java.util.Map;

public interface ShardingStrategy {

    /**
     * 计算目标分片名称
     *
     * @param availableTargets 所有可用的目标名称列表（如 ["ds0","ds1"] 或 ["user0","user1","user2"]）
     * @param logicTable       逻辑表名（如 "user"）
     * @param params           分片参数 Map（如 {"id": 3}）
     * @return 目标名称（如 "ds1" 或 "user0"）
     */
    String doSharding(List<String> availableTargets, String logicTable, Map<String, Object> params);
}