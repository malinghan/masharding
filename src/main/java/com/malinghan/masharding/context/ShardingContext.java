package com.malinghan.masharding.context;

public class ShardingContext {

    private static final ThreadLocal<ShardingResult> LOCAL = new ThreadLocal<>();

    public static void set(ShardingResult result) {
        LOCAL.set(result);
    }

    public static ShardingResult get() {
        return LOCAL.get();
    }

    // v7.0 会用到，此处先预留
    public static void remove() {
        LOCAL.remove();
    }
}