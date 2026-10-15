package com.cmic.engine.strategy;

/**
 * 属性值覆盖策略接口
 */
public interface AttributeOverrideStrategy {

    /**
     * 判断该策略是否支持处理此属性
     * @param attributeName 属性名
     * @return 是否支持
     */
    boolean supports(String attributeName);

    /**
     * 判断是否允许新值覆盖旧值
     * @param newValue 新值
     * @param oldValue 旧值
     * @return true-允许覆盖, false-不允许覆盖
     */
    boolean allowOverride(Object newValue, Object oldValue);
}