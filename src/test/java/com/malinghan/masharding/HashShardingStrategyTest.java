package com.malinghan.masharding;

import com.malinghan.masharding.strategy.HashShardingStrategy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class HashShardingStrategyTest {

    @Test
    public void testDatabaseSharding() {
        HashShardingStrategy strategy = new HashShardingStrategy("id", "ds${id % 2}");
        List<String> targets = Arrays.asList("ds0", "ds1");

        for (int id = 0; id <= 5; id++) {
            Map<String, Object> params = Collections.singletonMap("id", id);
            String result = strategy.doSharding(targets, "user", params);
            System.out.println("id=" + id + " → " + result);
        }
    }

    @Test
    public void testTableSharding() {
        HashShardingStrategy strategy = new HashShardingStrategy("id", "user${id % 3}");
        List<String> targets = Arrays.asList("user0", "user1", "user2");

        for (int id = 0; id <= 5; id++) {
            Map<String, Object> params = Collections.singletonMap("id", id);
            String result = strategy.doSharding(targets, "user", params);
            System.out.println("id=" + id + " → " + result);
        }
    }
}
