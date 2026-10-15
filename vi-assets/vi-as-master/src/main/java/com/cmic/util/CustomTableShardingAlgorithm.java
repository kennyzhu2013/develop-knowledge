package com.cmic.util;

import io.shardingsphere.api.algorithm.sharding.PreciseShardingValue;
import io.shardingsphere.api.algorithm.sharding.standard.PreciseShardingAlgorithm;

import java.util.Collection;

/**
 * @author XH
 * @description:
 * @date 2025/7/31 11:47
 */
public class CustomTableShardingAlgorithm implements PreciseShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> tableNames, PreciseShardingValue<Long> shardingValue) {
        // 获取number值
        long number = shardingValue.getValue();
        long suffix = (number % 100000000000L) / 100000000L;

        // 组合物理表名：order_xxx
        String targetTable = "base_prod_num_" + suffix;
        // 验证表是否存在 todo 不存在的策略
        if (tableNames.contains(targetTable)) {
            return targetTable;
        }
        throw new UnsupportedOperationException("表不存在: " + targetTable);
    }
}
