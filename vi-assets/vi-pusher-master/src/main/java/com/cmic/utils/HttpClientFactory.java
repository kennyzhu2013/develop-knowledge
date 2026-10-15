package com.cmic.utils;


import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.http.HttpClient;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Component
public class HttpClientFactory {


    public HttpClient createHttpClient(InetSocketAddress proxy) throws NoSuchAlgorithmException, KeyManagementException {
        //跳过ssl验证
        SSLContext sslContext = SSLContext.getInstance("SSL");
        //jdk11初始化ssl上下文的时候必须使用X509ExtendedTrustManager，不能使用X509TrustManager，不然https访问私有证书会报错
        TrustManager[] trustAllCerts = new TrustManager[]{new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }
        }};
        sslContext.init(null, trustAllCerts, new SecureRandom());

        //创建httpclient
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(15))
                .proxy(ProxySelector.of(proxy))
                .build();
        return client;

    }

    @Bean
    public HttpClient HttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("SSL");
        TrustManager[] trustAllCerts = new TrustManager[]{new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {

            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) throws CertificateException {

            }

            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) throws CertificateException {

            }
        }};
        sslContext.init(null, trustAllCerts, new SecureRandom());
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        return client;
    }
}