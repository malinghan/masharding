package com.malinghan.masharding.controller;

import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.engine.ShardingEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ShardingEngineController {

    @Autowired
    private ShardingEngine shardingEngine;

    @PostMapping("/engine/sharding")
    public ShardingResult calculateSharding(@RequestBody Map<String, Object> request) {
        String sql = (String) request.get("sql");
        Object[] args = ((java.util.List<?>) request.get("args")).toArray();
        
        return shardingEngine.sharding(sql, args);
    }
}