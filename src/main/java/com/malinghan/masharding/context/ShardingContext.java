package com.malinghan.masharding.context;

public class ShardingContext {

    private static final ThreadLocal<ShardingResult> LOCAL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> EXPLICITLY_CLEARED = new ThreadLocal<>();

    public static void set(ShardingResult result) {
        LOCAL.set(result);
        EXPLICITLY_CLEARED.set(false); // 重置清除标记
    }

    public static ShardingResult get() {
        return LOCAL.get();
    }

    public static boolean isExplicitlyCleared() {
        Boolean cleared = EXPLICITLY_CLEARED.get();
        return cleared != null && cleared;
    }

    // v7.0 会用到，此处先预留
    public static void remove() {
        LOCAL.remove();
        EXPLICITLY_CLEARED.set(true); // 标记为显式清除
    }
}