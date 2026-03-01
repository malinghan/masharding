package com.malinghan.masharding;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.mapper.UserMapper;
import com.malinghan.masharding.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TestRunner implements ApplicationRunner {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 手动设置路由到 ds0
        ShardingContext.set(new ShardingResult("ds0", ""));
        List<User> users0 = userMapper.findAll();
        System.out.println("ds0 users: " + users0);

        // 手动设置路由到 ds1
        ShardingContext.set(new ShardingResult("ds1", ""));
        List<User> users1 = userMapper.findAll();
        System.out.println("ds1 users: " + users1);

        // 并发验证：两个线程分别路由到不同数据源
        Thread t1 = new Thread(() -> {
            ShardingContext.set(new ShardingResult("ds0", ""));
            System.out.println("Thread-1 key: "
                + ShardingContext.get().getTargetDataSourceName());
        });
        Thread t2 = new Thread(() -> {
            ShardingContext.set(new ShardingResult("ds1", ""));
            System.out.println("Thread-2 key: "
                + ShardingContext.get().getTargetDataSourceName());
        });
        t1.start(); t2.start();
        t1.join();  t2.join();
    }
}