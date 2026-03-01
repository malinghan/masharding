package com.malinghan.masharding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "spring.sharding")
public class ShardingProperties {

    // getter / setter
    // key = "ds0" / "ds1"，value = 数据源配置项
    // datasources:  datasource-name  -> properties
    private Map<String, DataSourceProperties> datasources = new LinkedHashMap<>();

    public Map<String, DataSourceProperties> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, DataSourceProperties> datasources) {
        this.datasources = datasources;
    }


    @Getter
    @Setter
    public static class DataSourceProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "com.mysql.cj.jdbc.Driver";
        // getter / setter 省略
    }
}
