package com.malinghan.masharding.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.malinghan.masharding.datasource.ShardingDataSource;
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
            // 创建数据源 Druid
            DruidDataSource druid = new DruidDataSource();
            druid.setUrl(dsProps.getUrl());
            druid.setUsername(dsProps.getUsername());
            druid.setPassword(dsProps.getPassword());
            druid.setDriverClassName(dsProps.getDriverClassName());

            targetDataSources.put(dsName, druid);
        }

        ShardingDataSource shardingDataSource = new ShardingDataSource();
        shardingDataSource.setTargetDataSources(targetDataSources);
        // 设置默认数据源（找不到 key 时的兜底）
        shardingDataSource.setDefaultTargetDataSource(
                targetDataSources.values().iterator().next()
        );
        return shardingDataSource;
    }
}