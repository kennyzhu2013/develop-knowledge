package com.cmic.business.query;


import com.cmic.Java11HttpClient;
import com.cmic.service.XmodePromExporterService;
import net.sf.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AsyncJava11HttpClient {
    private static final Logger logger = LoggerFactory.getLogger(AsyncJava11HttpClient.class);
    static volatile HttpClient auditClient;
    static volatile HttpClient defaultClient;
    static volatile HttpClient aliClient;
    static volatile HttpClient industrialIdQueryClient;


    public static void postJson(String billId, String url, Map<String, String> headers, Object jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Version version) {
        try {
            postJsonWithTimeout(billId, PostType.DEFAULT, url, headers, jsonObject, action, version, 10);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public enum PostType {
        AUDIT,
        DEFAULT,
        ALI,
        INDUSTRIAL
    }

    /**
     * 发送http post请求到指定的url，使用指定timeout值（毫秒为单位）
     *
     * @param url
     * @param headers
     * @param jsonObject
     * @param action
     * @param version
     * @param timeout
     */
    public static void postJsonWithTimeoutMill(String billId, PostType postType, String url, Map<String, String> headers, Object jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Version version, int timeout) throws InterruptedException {
        postJsonWithTimeoutMillWithTag(billId, postType, url, headers, jsonObject, action, version, timeout, "default");
    }

    /**
     * 发送http post请求到指定的url，使用指定timeout值（毫秒为单位），增加忽略timeoutlog的开关
     *
     * @param url
     * @param headers
     * @param jsonObject
     * @param action
     * @param version
     * @param timeout
     */
    public static void postJsonWithTimeoutMillWithTag(String billId, PostType postType, String url, Map<String, String> headers, Object jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Version version, int timeout, String businessTag) {
        HttpClient client = getClientInstance(postType);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json");
        for (Map.Entry<String, String> en : headers.entrySet()) {
            builder.header(en.getKey(), en.getValue());
        }
        HttpRequest request = builder.timeout(Duration.ofMillis(timeout))
                .POST(BodyPublishers.ofString(jsonObject.toString())).version(version)
                .build();

        client.sendAsync(request, BodyHandlers.ofString()).completeOnTimeout(getErrorResponse(new TimeoutException(billId + "， sendAsync timeout")), timeout + 100, TimeUnit.MILLISECONDS).exceptionally(throwable -> {
                    if (throwable instanceof HttpTimeoutException) {
                        logger.error("{}, exceptionally - apply ：query:{} http query timeout", billId, url);
                        XmodePromExporterService.getInstance().incHttpError(businessTag, "timeout");
                    } else {
                        logger.error("{}, exceptionally - apply ：", billId, throwable);
                        XmodePromExporterService.getInstance().incHttpError(businessTag, "connect");
                    }
                    return getErrorResponse(throwable);
                })
                .thenApply(t -> {
                    JSONObject jsonRes = new JSONObject();
                    try {
                        int code = t.statusCode();
                        if (code != 200) {
                            XmodePromExporterService.getInstance().incHttpError(businessTag, "codeNot200");
                            jsonRes.element("http_code", code);
                            jsonRes.element("error msg", t.body());
                            return jsonRes;
                        }
                        jsonRes = JSONObject.fromObject(t.body());
                        jsonRes.element("http_code", code);
                        return jsonRes;
                    } catch (Exception e) {
                        logger.error("{}, sendAsync process response error", billId, e);
                        XmodePromExporterService.getInstance().incHttpError(businessTag, "dealError");
                        jsonRes = new JSONObject();
                        jsonRes.element("http_code", -1);
                        jsonRes.element("error msg", e.toString());
                        logger.error("jsonRequest: {}", jsonObject, e);
                        return jsonRes;
                    }
                })
                .thenAcceptAsync(action::apply, getResponsePool(postType));
    }

    private static HttpClient getClientInstance(PostType postType) {
        switch (postType) {
            case AUDIT:
                if (auditClient == null) {
                    synchronized (AsyncJava11HttpClient.class) {
                        if (auditClient == null) {
                            auditClient = HttpClient.newBuilder().executor(ThreadPoolService.getAuditQueryExecutorInstance()).connectTimeout(Duration.ofMillis(300)).build();
                        }
                    }
                }
                return auditClient;
            case ALI:
                if (aliClient == null) {
                    synchronized (AsyncJava11HttpClient.class) {
                        if (aliClient == null) {
                            aliClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(5000)).executor(ThreadPoolService.getAliQueryExecutorInstance()).build();
                        }
                    }
                }
                return aliClient;
            case INDUSTRIAL:
                if (industrialIdQueryClient == null) {
                    synchronized (AsyncJava11HttpClient.class) {
                        if (industrialIdQueryClient == null) {
                            // 创建信任所有证书的 TrustManager
                            TrustManager[] trustAllCerts = new TrustManager[]{
                                    new X509TrustManager() {
                                        @Override
                                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                                        }

                                        @Override
                                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                                        }

                                        @Override
                                        public X509Certificate[] getAcceptedIssuers() {
                                            return new X509Certificate[0];
                                        }
                                    }
                            };
                            // 创建禁用主机名验证的SSL参数
                            SSLParameters sslParameters = new SSLParameters();
                            sslParameters.setEndpointIdentificationAlgorithm(""); // 禁用主机名验证
                            SSLContext sslContext = null;
                            try {
                                sslContext = SSLContext.getInstance("TLS");
                                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                                throw new RuntimeException(e);
                            }
                            industrialIdQueryClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300))
                                    .sslContext(sslContext)
                                    .sslParameters(sslParameters)
                                    .executor(ThreadPoolService.getIndustrialIdQuerySendPool()).build();
                        }
                    }
                }
                return industrialIdQueryClient;
            default:
                if (defaultClient == null) {
                    synchronized (AsyncJava11HttpClient.class) {
                        if (defaultClient == null) {
                            defaultClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(5000)).executor(ThreadPoolService.getDefaultQueryExecutorInstance()).build();
                        }
                    }
                }
                return defaultClient;
        }
    }

    private static ThreadPoolExecutor getResponsePool(PostType postType) {
        switch (postType) {
            case AUDIT:
                return ThreadPoolService.getAuditResponseExecutorInstance();
            case ALI:
                return ThreadPoolService.getAliQueryExecutorInstance();
            case INDUSTRIAL:
                return ThreadPoolService.getIndustrialIdQuerySendPool();
            default:
                return ThreadPoolService.getDefaultQueryExecutorInstance();
        }
    }

    private static HttpResponse<String> getErrorResponse(Throwable throwable) {
        return new HttpResponse<String>() {
            @Override
            public int statusCode() {
                return -1;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public String body() {
                return throwable.toString();
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return null;
            }

            @Override
            public Version version() {
                return null;
            }
        };
    }

    /**
     * 发送http post请求到指定的url，使用指定timeout值（秒为单位）
     *
     * @param url
     * @param headers
     * @param jsonObject
     * @param action
     * @param version
     * @param timeout
     */
    public static void postJsonWithTimeout(String billId, PostType postType, String url, Map<String, String> headers, Object jsonObject, Java11HttpClient.httpCallback<JSONObject> action, Version version, int timeout) throws InterruptedException {
        int timeOutMill = timeout * 1000;
        postJsonWithTimeoutMill(billId, postType, url, headers, jsonObject, action, version, timeOutMill);
    }


    public void postFile() throws FileNotFoundException {
        HttpRequest request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(URI.create("http://httpbin.org/post"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "octet-stream")
                .POST(BodyPublishers.ofFile(Paths.get("Java11HttpClient.java")))
                .build();
        HttpClient client = HttpClient.newBuilder().build();
        client.sendAsync(request, BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println);

    }

    public void postMultipart() throws FileNotFoundException {
        String boundary = "B-0-u-n-d-a-r-y";
        String data = "Hello Yang!";
        HttpRequest request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(URI.create("http://httpbin.org/post"))
                .timeout(Duration.ofSeconds(2))
                .header("Content-Type", "multipart/form-data, boundary=" + boundary)
                .POST(BodyPublishers.ofString("--" + boundary + "\r\n"
                        + "content-disposition: form-data; filename=\"text\"\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "\r\n"
                        + data + "\r\n"
                        + "--" + boundary + "--\r\n"))
                .build();
        HttpClient client = HttpClient.newBuilder().build();
        client.sendAsync(request, BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(System.out::println);
    }

    public void httpSync() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(URI.create("http://httpbin.org/get"))
                .GET().build();
        HttpClient client = HttpClient.newBuilder().build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
    }

    public void httpAsync() {
        HttpRequest request = HttpRequest.newBuilder()
                .version(Version.HTTP_1_1)
                .uri(URI.create("http://httpbin.org/get"))
                .GET().build();
        HttpClient client = HttpClient.newBuilder().build();
        client.sendAsync(request, BodyHandlers.ofString())
                .thenApply(response -> {
                    System.out.println(response.statusCode());
                    return response;
                }).thenApply(HttpResponse::body)
                .thenAccept(body -> System.out.println(body));
    }


}