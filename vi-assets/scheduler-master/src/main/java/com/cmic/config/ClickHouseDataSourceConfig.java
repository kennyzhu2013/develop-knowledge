package com.cmic.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Properties;

//@Configuration
public class ClickHouseDataSourceConfig {
    @Value("${spring.data.clickhouse.url}")
    private String url;
    @Value("${spring.data.clickhouse.username}")
    private String username;
    @Value("${spring.data.clickhouse.password}")
    private String password;
    @Value("${spring.data.clickhouse.driver-class-name}")
    private String driverClassName;

    @Bean(name = "clickhouseDataSource")
    public DataSource clickhouseDataSource() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", username);
        properties.setProperty("password", password);
        properties.setProperty("driverClassName", driverClassName);
        return new ClickHouseDataSource(url, properties);
    }

    @Bean(name = "clickhouseJdbcTemplate")
    public JdbcTemplate clickhouseJdbcTemplate(@Qualifier("clickhouseDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
