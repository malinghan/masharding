package com.malinghan.masharding;

import com.malinghan.masharding.datasource.ShardingDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TestRunner implements ApplicationRunner {

    @Autowired
    private ShardingDataSource shardingDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 切换到 ds0
        shardingDataSource.setCurrentKey("ds0");
        List<Map<String, Object>> result0 = jdbcTemplate.queryForList("select * from test");
        System.out.println("ds0 result: " + result0);

        // 切换到 ds1
        shardingDataSource.setCurrentKey("ds1");
        List<Map<String, Object>> result1 = jdbcTemplate.queryForList("select * from test");
        System.out.println("ds1 result: " + result1);
    }
}