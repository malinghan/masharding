package com.malinghan.masharding;

import com.malinghan.masharding.config.ShardingProperties;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.engine.StandardShardingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class StandardShardingEngineTest {

    private StandardShardingEngine engine;

    @BeforeEach
    public void setUp() {
        ShardingProperties props = new ShardingProperties();

        // 配置数据源
        Map<String, ShardingProperties.DataSourceProperties> ds = new LinkedHashMap<>();
        ds.put("ds0", mockDsProps("db0"));
        ds.put("ds1", mockDsProps("db1"));
        props.setDatasources(ds);

        // 配置数据库策略
        props.setDatabaseStrategy(new ShardingProperties.StrategyProperties("id", "ds${id % 2}"));

        // 配置表策略
        Map<String, ShardingProperties.StrategyProperties> ts = new LinkedHashMap<>();
        ts.put("user", new ShardingProperties.StrategyProperties("id", "user${id % 3}"));
        props.setTableStrategies(ts);

        // 配置物理表列表
        props.setActualTables(Collections.singletonMap(
            "user", Arrays.asList("user0", "user1", "user2")));

        engine = new StandardShardingEngine(props);
    }

    private ShardingProperties.DataSourceProperties mockDsProps(String dbName) {
        ShardingProperties.DataSourceProperties dsProps = new ShardingProperties.DataSourceProperties();
        dsProps.setUrl("jdbc:mysql://localhost:3306/" + dbName);
        dsProps.setUsername("root");
        dsProps.setPassword("123456");
        return dsProps;
    }

    @Test
    public void testInsert() {
        ShardingResult result = engine.sharding(
            "insert into user(id,name,age) values(?,?,?)",
            new Object[]{3, "ma", 20}
        );
        // id=3: ds${3%2}=ds1, user${3%3}=user0
        assertEquals("ds1", result.getTargetDataSourceName());
        assertTrue(result.getTargetSqlStatement().contains("user0"));
        System.out.println("INSERT result: " + result);
    }

    @Test
    public void testSelect() {
        ShardingResult result = engine.sharding(
            "select * from user where id=?",
            new Object[]{4}
        );
        // id=4: ds${4%2}=ds0, user${4%3}=user1
        assertEquals("ds0", result.getTargetDataSourceName());
        assertTrue(result.getTargetSqlStatement().contains("user1"));
        System.out.println("SELECT result: " + result);
    }

    @Test
    public void testUpdate() {
        ShardingResult result = engine.sharding(
            "update user set name=? where id=?",
            new Object[]{"newname", 5}
        );
        // id=5: ds${5%2}=ds1, user${5%3}=user2
        assertEquals("ds1", result.getTargetDataSourceName());
        assertTrue(result.getTargetSqlStatement().contains("user2"));
        System.out.println("UPDATE result: " + result);
    }

    @Test
    public void testDelete() {
        ShardingResult result = engine.sharding(
            "delete from user where id=?",
            new Object[]{2}
        );
        // id=2: ds${2%2}=ds0, user${2%3}=user2
        assertEquals("ds0", result.getTargetDataSourceName());
        assertTrue(result.getTargetSqlStatement().contains("user2"));
        System.out.println("DELETE result: " + result);
    }
}
