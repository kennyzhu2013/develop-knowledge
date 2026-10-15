package com.cmic.config;


import com.cmic.config.metrics.DBMetrics;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * StarRocks数据源配置
 * StarRocks兼容MySQL协议，使用HikariCP连接池保证生产环境稳定性
 */
@Configuration
public class StarRocksDataSourceConfig {
    // 从yml读取StarRocks连接信息
    @Value("${spring.data.starRocks.url}")
    private String url;
    @Value("${spring.data.starRocks.username}")
    private String username;
    @Value("${spring.data.starRocks.password}")
    private String password;
    @Value("${spring.data.starRocks.driver-class-name}")
    private String driverClassName;
    @Resource
    private DBMetrics dbMetrics;

    /**
     * 注册StarRocks数据源Bean
     */
    @Bean(name = "starRocksDataSource")
    public DataSource starRocksDataSource() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);    // 最大连接数
        config.setMinimumIdle(2);         // 最小空闲连接
        config.setConnectionTimeout(30000);// 连接超时30秒
        config.setIdleTimeout(600000);    // 空闲连接超时10分钟
        config.setPoolName("starrocks-hikari-pool");
        return JdbcMonitorHelper.wrapDataSource(new HikariDataSource(config), "starRocks", dbMetrics);
    }

    /**
     * 注册StarRocks的JdbcTemplate Bean
     * 不标@Primary，保证CK的JdbcTemplate为默认注入
     */
    @Bean(name = "starRocksJdbcTemplate")
    public JdbcTemplate starRocksJdbcTemplate(@Qualifier("starRocksDataSource") DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(30);
        return JdbcMonitorHelper.wrapJdbc(jdbcTemplate, "starRocks", dbMetrics);
    }

    @Bean(name = "starRocksNamedJdbcTemplate")
    public NamedParameterJdbcTemplate starRocksNamedJdbcTemplate(@Qualifier("starRocksDataSource") DataSource starRocksDataSource) {
        NamedParameterJdbcTemplate namedJdbcTemplate = new NamedParameterJdbcTemplate(starRocksDataSource);
        return JdbcMonitorHelper.wrapNamedJdbc(namedJdbcTemplate, "starRocks", dbMetrics);
    }
}