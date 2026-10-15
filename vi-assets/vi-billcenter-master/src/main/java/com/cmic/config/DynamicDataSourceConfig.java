package com.cmic.config;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@AutoConfigureBefore(DynamicDataSourceAutoConfiguration.class)
@Import(DynamicDataSourceAutoConfiguration.class)
public class DynamicDataSourceConfig {
    // 确保动态数据源正确配置
}
