package com.cmic.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() throws Exception {
        // 使用自定义工厂创建 RestTemplate
        return new RestTemplate(customRequestFactory());
    }

    @Bean
    public ClientHttpRequestFactory customRequestFactory() throws Exception {
        return new HttpComponentsClientHttpRequestFactory(httpClient());
    }


    @Bean
    public CloseableHttpClient httpClient() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        // 1. 创建SSL上下文
        SSLContext sslContext = org.apache.hc.core5.ssl.SSLContextBuilder.create()
                .setProtocol("TLSv1.3")
                .build();

        // 2. 创建SSL连接工厂
        org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory connectionSocketFactory = new org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory(
                sslContext,
                null, // 密码套件
                new String[]{"TLSv1.3", "TLSv1.2"}, // 支持的协议
                org.apache.hc.client5.http.ssl.NoopHostnameVerifier.INSTANCE // 主机名验证
        );

        // 3. 注册连接工厂
        org.apache.hc.core5.http.config.Registry<org.apache.hc.client5.http.socket.ConnectionSocketFactory> registry = org.apache.hc.core5.http.config.RegistryBuilder.<org.apache.hc.client5.http.socket.ConnectionSocketFactory>create()
                .register("http", org.apache.hc.client5.http.socket.PlainConnectionSocketFactory.getSocketFactory())
                .register("https", connectionSocketFactory)
                .build();
        // 4. 创建连接池管理器
        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager connectionManager = new org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager(registry);
        connectionManager.setMaxTotal(3000);
        connectionManager.setDefaultMaxPerRoute(300);

        // 5. 创建请求配置（使用新的Timeout API）
        org.apache.hc.client5.http.config.RequestConfig requestConfig = org.apache.hc.client5.http.config.RequestConfig.custom()
                .setResponseTimeout(Timeout.of(8, TimeUnit.SECONDS)) // 替代setSocketTimeout
                .setConnectTimeout(Timeout.of(2, TimeUnit.SECONDS))  // 替代setConnectTimeout
                .setConnectionRequestTimeout(Timeout.of(200, TimeUnit.MILLISECONDS)) // 替代setConnectionRequestTimeout
                .build();

        // 6. 创建HttpClient（不再需要单独设置SSL相关属性）
        return org.apache.hc.client5.http.impl.classic.HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connectionManager) // 连接管理器已包含SSL配置
                .build();
    }

}

