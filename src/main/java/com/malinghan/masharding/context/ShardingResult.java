package com.malinghan.masharding.context;

public class ShardingResult {

    // 目标数据源名称，如 "ds0" / "ds1"
    private final String targetDataSourceName;

    // 改写后的物理 SQL，如 "select * from user1 where id=?"
    // v2.0 中暂时不使用，为后续版本预留
    private final String targetSqlStatement;

    public ShardingResult(String targetDataSourceName, String targetSqlStatement) {
        this.targetDataSourceName = targetDataSourceName;
        this.targetSqlStatement = targetSqlStatement;
    }

    public String getTargetDataSourceName() {
        return targetDataSourceName;
    }

    public String getTargetSqlStatement() {
        return targetSqlStatement;
    }

    @Override
    public String toString() {
        return "ShardingResult{ds=" + targetDataSourceName
                + ", sql=" + targetSqlStatement + "}";
    }
}