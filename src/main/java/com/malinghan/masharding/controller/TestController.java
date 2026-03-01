package com.malinghan.masharding.controller;

import com.malinghan.masharding.datasource.ShardingDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private ShardingDataSource shardingDataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test")
    public List<Map<String, Object>> query(@RequestParam String ds) {
        shardingDataSource.setCurrentKey(ds);
        return jdbcTemplate.queryForList("select * from test");
    }
}
