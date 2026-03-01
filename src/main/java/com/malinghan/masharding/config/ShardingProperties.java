package com.malinghan.masharding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "spring.sharding")
public class ShardingProperties {

    // 分库策略配置
    private StrategyProperties databaseStrategy;

    private Map<String, DataSourceProperties> datasources;

    // 分表策略配置（按逻辑表名区分）
    private Map<String, StrategyProperties> tableStrategies = new LinkedHashMap<>();

    // 各逻辑表对应的物理表列表
    private Map<String, List<String>> actualTables = new LinkedHashMap<>();

    public Map<String, DataSourceProperties> getDatasources() { return datasources; }
    public void setDatasources(Map<String, DataSourceProperties> datasources) { this.datasources = datasources; }

    public StrategyProperties getDatabaseStrategy() { return databaseStrategy; }
    public void setDatabaseStrategy(StrategyProperties databaseStrategy) { this.databaseStrategy = databaseStrategy; }

    public Map<String, StrategyProperties> getTableStrategies() { return tableStrategies; }
    public void setTableStrategies(Map<String, StrategyProperties> tableStrategies) { this.tableStrategies = tableStrategies; }

    public Map<String, List<String>> getActualTables() { return actualTables; }
    public void setActualTables(Map<String, List<String>> actualTables) { this.actualTables = actualTables; }


    @Getter
    @Setter
    public static class DataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        // getter / setter 省略
    }

    @Getter
    @Setter
    public static class StrategyProperties {
        private String shardingColumn;
        private String algorithmExpression;

        public StrategyProperties() {}

        public StrategyProperties(String shardingColumn, String algorithmExpression) {
            this.shardingColumn = shardingColumn;
            this.algorithmExpression = algorithmExpression;
        }
    }
}
