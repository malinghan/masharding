package com.malinghan.masharding.mapper;

import com.malinghan.masharding.context.ShardingContext;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.engine.ShardingEngine;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class ShardingMapperFactoryBean<T> extends MapperFactoryBean<T> {

    @Autowired
    private ShardingEngine shardingEngine;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    public ShardingMapperFactoryBean() {}

    public ShardingMapperFactoryBean(Class<T> mapperInterface) {
        super(mapperInterface);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() throws Exception {
        T originalMapper = super.getObject();
        Class<T> mapperInterface = getObjectType();

        return (T) Proxy.newProxyInstance(
            mapperInterface.getClassLoader(),
            new Class[]{mapperInterface},
            new ShardingInvocationHandler(originalMapper, mapperInterface)
        );
    }

    private class ShardingInvocationHandler implements InvocationHandler {

        private final T originalMapper;
        private final Class<T> mapperInterface;

        ShardingInvocationHandler(T originalMapper, Class<T> mapperInterface) {
            this.originalMapper = originalMapper;
            this.mapperInterface = mapperInterface;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(originalMapper, args);
            }

            String mapperId = mapperInterface.getName() + "." + method.getName();
            org.apache.ibatis.session.Configuration configuration =
                sqlSessionFactory.getConfiguration();

            if (!configuration.hasStatement(mapperId)) {
                return method.invoke(originalMapper, args);
            }

            MappedStatement ms = configuration.getMappedStatement(mapperId);
            Object paramObject = buildParamObject(args);
            BoundSql boundSql = ms.getBoundSql(paramObject);
            String sql = boundSql.getSql().trim();
            Object[] flatArgs = flattenArgs(boundSql, paramObject);

            // 检查是否需要进行分片计算
            ShardingResult existingResult = ShardingContext.get();
            boolean skipSharding = false;
            
            if (existingResult == null) {
                // 检查是否是显式清除的上下文
                if (ShardingContext.isExplicitlyCleared()) {
                    skipSharding = true;
                } else {
                    // 如果上下文为空且非显式清除，则进行分片计算
                    ShardingResult result = shardingEngine.sharding(sql, flatArgs);
                    ShardingContext.set(result);
                }
            } else if (existingResult.getTargetSqlStatement() == null || 
                      existingResult.getTargetSqlStatement().isEmpty()) {
                // 如果上下文存在但没有目标SQL，则进行分片计算
                ShardingResult result = shardingEngine.sharding(sql, flatArgs);
                ShardingContext.set(result);
            }
            // 如果上下文中已有完整的结果，则不进行重复计算

            try {
                return method.invoke(originalMapper, args);
            } finally {
                // v7.0 会在此处添加 ShardingContext.remove()
            }
        }

        private Object[] flattenArgs(BoundSql boundSql, Object paramObject) {
            List<ParameterMapping> mappings = boundSql.getParameterMappings();
            if (mappings == null || mappings.isEmpty()) {
                return new Object[0];
            }

            MetaObject metaParam = SystemMetaObject.forObject(paramObject);
            Object[] result = new Object[mappings.size()];
            for (int i = 0; i < mappings.size(); i++) {
                String propName = mappings.get(i).getProperty();
                if (metaParam.hasGetter(propName)) {
                    result[i] = metaParam.getValue(propName);
                } else if (boundSql.hasAdditionalParameter(propName)) {
                    result[i] = boundSql.getAdditionalParameter(propName);
                }
            }
            return result;
        }

        private Object buildParamObject(Object[] args) {
            if (args == null || args.length == 0) return null;
            if (args.length == 1) return args[0];
            return args[0];
        }
    }
}
