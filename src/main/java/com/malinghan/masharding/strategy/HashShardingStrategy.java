package com.malinghan.masharding.strategy;

import java.util.List;
import java.util.Map;

public class HashShardingStrategy implements ShardingStrategy {

    // 分片列名，如 "id"
    private final String shardingColumn;

    // 内联表达式，如 "ds${id % 2}"
    private final String algorithmExpression;

    private final InlineExpressionParser parser;

    public HashShardingStrategy(String shardingColumn, String algorithmExpression) {
        this.shardingColumn = shardingColumn;
        this.algorithmExpression = algorithmExpression;
        this.parser = new InlineExpressionParser(algorithmExpression);
    }

    @Override
    public String doSharding(List<String> availableTargets,
                             String logicTable,
                             Map<String, Object> params) {
        // 从参数 Map 中取出分片列的值
        Object shardingValue = params.get(shardingColumn);
        if (shardingValue == null) {
            throw new IllegalArgumentException(
                "分片列 [" + shardingColumn + "] 的值不能为 null");
        }

        // 执行 Groovy 表达式计算目标名称
        String target = parser.evaluate(params);

        // 验证计算结果在可用目标列表中
        if (!availableTargets.contains(target)) {
            throw new IllegalStateException(
                "计算结果 [" + target + "] 不在可用目标列表 " + availableTargets + " 中");
        }

        return target;
    }
}