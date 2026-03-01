package com.malinghan.masharding.datasource;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ShardingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        ShardingResult result = ShardingContext.get();
        if (result == null) {
            return null; // 返回 null 时使用默认数据源
        }
        String key = result.getTargetDataSourceName();
        System.out.println("determineCurrentLookupKey = " + key);
        return key;
    }
}