package com.malinghan.masharding.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class ShardingDataSource extends AbstractRoutingDataSource {

    // v1.0 硬编码，仅用于验证路由机制
    private String currentKey = "ds0";

    public void setCurrentKey(String key) {
        this.currentKey = key;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        System.out.println("determineCurrentLookupKey = " + currentKey);
        return currentKey;
    }
}
