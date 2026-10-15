package com.cmic.engine.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RiskLevelOverrideStrategy implements AttributeOverrideStrategy {

    // 定义风险等级权重：数值越小，风险越高，优先级越高
    private static final Map<String, Integer> RISK_PRIORITY = Map.of(
            "1", 10, // 高风险 (权重最高)
            "2", 5,  // 中风险
            "3", 1   // 低风险 (权重最低)
    );

    @Override
    public boolean supports(String attributeName) {
        return "riskLevel".equals(attributeName);
    }

    @Override
    public boolean allowOverride(Object newValue, Object oldValue) {
        if (newValue == null || oldValue == null) {
            return true; // 空值处理策略：通常允许设置或允许清空
        }

        int newPriority = RISK_PRIORITY.getOrDefault(String.valueOf(newValue), 0);
        int oldPriority = RISK_PRIORITY.getOrDefault(String.valueOf(oldValue), 0);

        // 只有当新值的优先级 >= 旧值优先级时，才允许覆盖
        // (即：高风险可以覆盖低风险，低风险不能覆盖高风险)
        boolean shouldOverride = newPriority >= oldPriority;

        if (!shouldOverride) {
            log.info("RiskLevel update blocked: lower risk [{}] cannot override higher risk [{}].", newValue, oldValue);
        }

        return shouldOverride;
    }
}

