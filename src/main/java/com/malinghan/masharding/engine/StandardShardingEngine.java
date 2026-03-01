package com.malinghan.masharding.engine;

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.stat.TableStat;
import com.malinghan.masharding.config.ShardingProperties;
import com.malinghan.masharding.context.ShardingResult;
import com.malinghan.masharding.strategy.HashShardingStrategy;
import com.malinghan.masharding.strategy.ShardingStrategy;

import java.util.*;

public class StandardShardingEngine implements ShardingEngine {

    private final ShardingProperties properties;
    private final ShardingStrategy databaseStrategy;
    private final Map<String, ShardingStrategy> tableStrategies;

    public StandardShardingEngine(ShardingProperties properties) {
        this.properties = properties;

        ShardingProperties.StrategyProperties dbStrategyProps = properties.getDatabaseStrategy();
        this.databaseStrategy = new HashShardingStrategy(
            dbStrategyProps.getShardingColumn(),
            dbStrategyProps.getAlgorithmExpression()
        );

        this.tableStrategies = new LinkedHashMap<>();
        for (Map.Entry<String, ShardingProperties.StrategyProperties> entry
                : properties.getTableStrategies().entrySet()) {
            tableStrategies.put(entry.getKey(), new HashShardingStrategy(
                entry.getValue().getShardingColumn(),
                entry.getValue().getAlgorithmExpression()
            ));
        }
    }

    @Override
    public ShardingResult sharding(String sql, Object[] args) {
        SQLStatement statement = SQLUtils.parseSingleMysqlStatement(sql);

        String logicTable;
        Map<String, Object> shardingParams;

        if (statement instanceof SQLInsertStatement) {
            SQLInsertStatement insert = (SQLInsertStatement) statement;
            logicTable = insert.getTableName().getSimpleName().toLowerCase();
            shardingParams = extractInsertParams(insert, args);
        } else if (statement instanceof SQLUpdateStatement) {
            SQLUpdateStatement update = (SQLUpdateStatement) statement;
            logicTable = update.getTableName().getSimpleName().toLowerCase();
            shardingParams = extractUpdateWhereParams(update, args);
        } else {
            MySqlSchemaStatVisitor visitor = new MySqlSchemaStatVisitor();
            statement.accept(visitor);
            logicTable = visitor.getTables().keySet().iterator().next()
                .getName().toLowerCase();
            shardingParams = extractConditionParams(visitor, args);
        }

        List<String> availableDataSources = new ArrayList<>(properties.getDatasources().keySet());
        List<String> availableTables = properties.getActualTables()
            .getOrDefault(logicTable, Collections.emptyList());

        String targetDataSource = databaseStrategy.doSharding(
            availableDataSources, logicTable, shardingParams);

        ShardingStrategy tableStrategy = tableStrategies.get(logicTable);
        String targetTable = tableStrategy.doSharding(
            availableTables, logicTable, shardingParams);

        String targetSql = sql.replaceAll("(?i)\\b" + logicTable + "\\b", targetTable);

        System.out.println("target db.table = " + targetDataSource + "." + targetTable);

        return new ShardingResult(targetDataSource, targetSql);
    }

    private Map<String, Object> extractInsertParams(SQLInsertStatement insert, Object[] args) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<com.alibaba.druid.sql.ast.SQLExpr> columns = insert.getColumns();
        for (int i = 0; i < columns.size() && i < args.length; i++) {
            String colName = ((SQLIdentifierExpr) columns.get(i)).getName().toLowerCase();
            params.put(colName, args[i]);
        }
        return params;
    }

    // For UPDATE: count SET items to skip them, then align WHERE conditions with remaining args
    private Map<String, Object> extractUpdateWhereParams(SQLUpdateStatement update, Object[] args) {
        int setCount = update.getItems().size();
        Map<String, Object> params = new LinkedHashMap<>();
        if (update.getWhere() instanceof SQLBinaryOpExpr) {
            collectWhereParams((SQLBinaryOpExpr) update.getWhere(), args, setCount, params);
        }
        return params;
    }

    private int collectWhereParams(SQLBinaryOpExpr expr, Object[] args, int offset,
                                   Map<String, Object> params) {
        // Recurse left side first (for AND chains)
        if (expr.getLeft() instanceof SQLBinaryOpExpr) {
            offset = collectWhereParams((SQLBinaryOpExpr) expr.getLeft(), args, offset, params);
        }
        // If right side is a placeholder, this is a leaf condition
        if (expr.getRight().toString().equals("?") && expr.getLeft() instanceof SQLIdentifierExpr) {
            String colName = ((SQLIdentifierExpr) expr.getLeft()).getName().toLowerCase();
            if (offset < args.length) {
                params.put(colName, args[offset]);
            }
            offset++;
        }
        return offset;
    }

    private Map<String, Object> extractConditionParams(MySqlSchemaStatVisitor visitor, Object[] args) {
        Map<String, Object> params = new LinkedHashMap<>();
        int argIndex = 0;
        for (TableStat.Condition condition : visitor.getConditions()) {
            String colName = condition.getColumn().getName().toLowerCase();
            if (argIndex < args.length) {
                params.put(colName, args[argIndex++]);
            }
        }
        return params;
    }
}
