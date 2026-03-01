package com.malinghan.masharding.controller;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.mapper.UserMapper;
import com.malinghan.masharding.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
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
}
