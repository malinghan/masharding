package com.malinghan.masharding.interceptor;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.sql.Connection;

@Intercepts({
    @Signature(
        type  = StatementHandler.class,
        method = "prepare",
        args  = {Connection.class, Integer.class}
    )
})
public class SqlStatementInterceptor implements Interceptor {

    private static final Unsafe UNSAFE;
    private static final long SQL_FIELD_OFFSET;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

            Field sqlField = BoundSql.class.getDeclaredField("sql");
            SQL_FIELD_OFFSET = UNSAFE.objectFieldOffset(sqlField);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(handler);

        // 解包代理，获取真实 StatementHandler
        StatementHandler realHandler = null;
        if (metaObject.hasGetter("h.target")) {
            realHandler = (StatementHandler) metaObject.getValue("h.target");
        }

        BoundSql boundSql = realHandler != null
            ? realHandler.getBoundSql()
            : handler.getBoundSql();

        ShardingResult result = ShardingContext.get();

        if (result != null) {
            String targetSql = result.getTargetSqlStatement();
            String originalSql = boundSql.getSql();

            if (targetSql != null && !targetSql.isEmpty()
                    && !targetSql.equals(originalSql)) {
                UNSAFE.putObject(boundSql, SQL_FIELD_OFFSET, targetSql);
                System.out.println("SQL replaced: [" + originalSql + "] → [" + targetSql + "]");
            }
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}
