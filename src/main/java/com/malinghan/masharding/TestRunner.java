package com.malinghan.masharding;

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
        System.out.println("=== v6.0 全链路测试：业务代码无需关心分片逻辑 ===");

        // 插入测试
        for (int id = 1; id <= 6; id++) {
            User user = new User(id, "user-" + id, 20 + id);
            userMapper.insert(user);
            System.out.println("Inserted user id=" + id);
        }

        System.out.println("\n=== 查询验证 ===");
        // 查询验证
        for (int id = 1; id <= 6; id++) {
            User user = userMapper.findById(id);
            System.out.println("Found: " + user);
        }
    }
}