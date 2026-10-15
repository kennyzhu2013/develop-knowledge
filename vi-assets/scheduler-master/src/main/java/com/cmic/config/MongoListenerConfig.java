package com.cmic.config;

import com.cmic.config.metrics.DBMetrics;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.mongodb.event.*;
import javax.annotation.Resource;

@Configuration
public class MongoListenerConfig {
    @Resource
    private DBMetrics dbMetrics;

    /**
     * 注册 MongoClient 自定义配置，添加监听器
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoListenerCustomizer() {
        return builder -> {
            // 添加命令执行失败监听器
            builder.addCommandListener(new CommandErrorListener());
            // 添加集群状态变更监听器
            builder.applyToClusterSettings(clusterBuilder -> clusterBuilder.addClusterListener(new ClusterDisableListener()));
        };
    }

    /**
     * 集群状态变更监听器
     * 重点捕获服务连接异常
     */
    private class ClusterDisableListener implements ClusterListener {
        @Override
        public void clusterDescriptionChanged(ClusterDescriptionChangedEvent event) {
            event.getNewDescription().getServerDescriptions().forEach(serverDesc -> {
                Throwable exception = serverDesc.getException();
                if (exception != null) {
                    dbMetrics.mongodbErrorInc("connect", exception);
                }
            });
        }
    }

    /**
     * 命令执行失败监听器
     */
    private class CommandErrorListener implements CommandListener {
        @Override
        public void commandFailed(CommandFailedEvent event) {
            dbMetrics.mongodbErrorInc(event.getCommandName(), event.getThrowable());
        }
    }
}

