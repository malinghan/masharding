package com.malinghan.masharding.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.malinghan.masharding.datasource.ShardingDataSource;
import com.malinghan.masharding.engine.ShardingEngine;
import com.malinghan.masharding.engine.StandardShardingEngine;
import com.malinghan.masharding.interceptor.SqlStatementInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(ShardingProperties.class)
public class ShardingAutoConfiguration {

    @Bean
    public ShardingDataSource shardingDataSource(ShardingProperties properties) {
        Map<Object, Object> targetDataSources = new LinkedHashMap<>();

        for (Map.Entry<String, ShardingProperties.DataSourceProperties> entry
                : properties.getDatasources().entrySet()) {
            String dsName = entry.getKey();
            ShardingProperties.DataSourceProperties dsProps = entry.getValue();
            DruidDataSource druid = new DruidDataSource();
            druid.setUrl(dsProps.getUrl());
            druid.setUsername(dsProps.getUsername());
            druid.setPassword(dsProps.getPassword());
            druid.setDriverClassName(dsProps.getDriverClassName());
            targetDataSources.put(dsName, druid);
        }

        ShardingDataSource shardingDataSource = new ShardingDataSource();
        shardingDataSource.setTargetDataSources(targetDataSources);
        shardingDataSource.setDefaultTargetDataSource(
                targetDataSources.values().iterator().next()
        );
        return shardingDataSource;
    }

    @Bean
    public ShardingEngine shardingEngine(ShardingProperties properties) {
        return new StandardShardingEngine(properties);
    }

    @Bean
    public SqlStatementInterceptor sqlStatementInterceptor() {
        return new SqlStatementInterceptor();
    }
}