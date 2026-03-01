package com.malinghan.masharding.strategy;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.util.HashMap;
import java.util.Map;

public class InlineExpressionParser {

    // 脚本缓存，避免重复编译（v8.0 会改为 ConcurrentHashMap）
    private static final Map<String, Script> SCRIPTS = new HashMap<>();

    private final String expression;

    public InlineExpressionParser(String expression) {
        this.expression = expression;
    }

    /**
     * 将 "ds${id % 2}" 转换为 Groovy closure 字符串
     * 使得变量可以通过 closure 的 delegate 访问
     */
    private String handlePlaceHolder(String expr) {
        // 将 ${varName} 替换为 ${it.varName}，使变量通过 it 访问
        return expr.replaceAll("\\$\\{(\\w+)", "\\${it.$1");
    }

    /**
     * 执行表达式，传入参数 Map，返回计算结果字符串
     */
    public String evaluate(Map<String, Object> params) {
        String closureExpr = "{it -> \"" + handlePlaceHolder(expression) + "\"}";

        Script script = SCRIPTS.computeIfAbsent(closureExpr, key -> {
            GroovyShell shell = new GroovyShell();
            return shell.parse(key);
        });

        // 执行脚本得到 Closure
        Closure<?> closure = (Closure<?>) script.run();

        // 使用 Binding 创建参数对象
        Binding binding = new Binding(params);
        closure.setDelegate(binding);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);

        return closure.call(binding).toString();
    }
}