package com.malinghan.masharding;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.mapper.UserMapper;
import com.malinghan.masharding.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class TestRunner implements ApplicationRunner {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println("=== v5.0 拦截器测试：SQL 替换验证 ===");

        // 测试 1: 验证拦截器存在并能正常工作
        System.out.println("\n--- 测试 1: 拦截器基础功能 ---");
        try {
            // 先插入一条测试数据
            User testUser = new User(999, "test-user", 25);
            userMapper.insert(testUser);
            System.out.println("插入测试数据成功");
            
            // 查询验证拦截器工作
            ShardingContext.set(new ShardingResult("ds0", ""));
            User foundUser = userMapper.findById(999);
            System.out.println("查询结果: " + foundUser);
        } catch (Exception e) {
            System.out.println("拦截器测试出现异常: " + e.getMessage());
        }

        // 测试 2: 验证 SQL 替换功能（如果分片上下文中有目标SQL）
        System.out.println("\n--- 测试 2: SQL 替换功能 ---");
        try {
            // 设置包含目标SQL的分片结果
            ShardingContext.set(new ShardingResult("ds0", "select * from user where id = 999"));
            User user = userMapper.findById(999);
            System.out.println("SQL 替换测试结果: " + user);
        } catch (Exception e) {
            System.out.println("SQL 替换测试出现异常: " + e.getMessage());
        }
        
        System.out.println("\n=== v5.0 测试完成 ===");
    }
}