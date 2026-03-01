package com.malinghan.masharding;

import com.malinghan.masharding.mapper.ShardingMapperFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(
    basePackages = "com.malinghan.masharding.mapper",
    factoryBean = ShardingMapperFactoryBean.class
)
public class MashardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MashardingApplication.class, args);
    }

}
