package com.malinghan.masharding.controller;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.mapper.UserMapper;
import com.malinghan.masharding.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TestController {

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/user")
    public List<User> query(@RequestParam String ds) {
        ShardingContext.set(new ShardingResult(ds, ""));
        return userMapper.findAll();
    }

    // v5.0 测试端点：设置分片上下文，SQL 应该被替换
    @GetMapping("/user/{id}")
    public User getUserWithSharding(@PathVariable int id) {
        // 手动设置：路由到 ds0，SQL 替换为 user1 版本
        ShardingContext.set(new ShardingResult("ds0", 
            "select * from user1 where id = ?"));
        
        // userMapper 原始 SQL 是 "select * from user where id = ?"
        // 拦截器会把它替换为 "select * from user1 where id = ?"
        return userMapper.findById(id);
    }

    // v5.0 测试端点：不设置分片上下文，SQL 应该保持原样
    @GetMapping("/user/raw/{id}")
    public User getUserWithoutSharding(@PathVariable int id) {
        // 清除 ShardingContext，让 Mapper 代理不进行分片计算
        ShardingContext.remove();
        return userMapper.findById(id);
    }
}
