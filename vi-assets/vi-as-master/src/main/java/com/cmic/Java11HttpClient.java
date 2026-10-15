package com.cmic;


import com.cmic.util.DtmfLogger;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSession;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

public class Java11HttpClient {
    private static final Logger logger = LoggerFactory.getLogger(Java11HttpClient.class);
    static HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();


    public static void postJson(String callId, int userId, String appId, String url, Map<String, String> headers, Object jsonObject, httpCallback<JSONObject> action, Version version) {
        postJsonWithTimeout(callId, userId, appId, url, headers, jsonObject, action, version, 10);
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
    public static void postJsonWithTimeoutMill(String callId, int userId, String appId, String url, Map<String, String> headers, Object jsonObject, httpCallback<JSONObject> action, Version version, int timeout) {
        HttpRequest.Builder builder = null;
        try {
            logger.info("postJsonWithTimeoutMill callId: {}, userId:{}, appId:{},url:{}", callId, userId, appId,url);
            builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json");
        } catch (Exception e) {
            logger.error("[{}] HttpRequest.Builder create error", callId, e);
            JSONObject jsonRes = new JSONObject();
            jsonRes.element("http_code", -1);
            jsonRes.element("error msg", e.getMessage());
            // 释放信令处理主线程，使用通用的线程处理转接查询后的业务流程
            CompletableFuture.runAsync(() -> action.apply(jsonRes));
        }
        if(Objects.isNull(builder)) return;
        for (Map.Entry<String, String> en : headers.entrySet()) {
            builder.header(en.getKey(), en.getValue());
        }
        HttpRequest request = builder.timeout(Duration.ofMillis(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString())).version(version)
                .build();
        long startTimeLong = System.currentTimeMillis();
        client.sendAsync(request, BodyHandlers.ofString()).completeOnTimeout(getErrorResponse(new TimeoutException(callId + "，query sendAsync timeout")), timeout + 100, TimeUnit.MILLISECONDS).exceptionally(throwable -> {
                    logger.error("[{}] userId:{}, appId:{} exceptionally - apply ：", callId, userId, appId,
                            throwable);
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
                })
                .thenApply(t -> {
                    JSONObject jsonRes = new JSONObject();
                    jsonRes.put("startTimeLong", startTimeLong);
                    try {
                        DtmfLogger.info(callId, "call query response t.body: {}", t.body());
                        int code = t.statusCode();
                        if (code != 200) {
                            jsonRes.element("http_code", code);
                            jsonRes.element("error msg", t.body());
                            return jsonRes;
                        }

                        JsonConfig jsonConfig = new JsonConfig();
                        jsonConfig.setJsonPropertyFilter((source, name, value) -> {
                            // 可以在这里过滤或修改字段名
                            return false; // 不过滤任何字段
                        });

                        jsonRes = JSONObject.fromObject(t.body(),jsonConfig);
                        jsonRes.element("http_code", code);
                        return jsonRes;
                    } catch (Exception e) {
                        logger.error("sendAsync process response error", e);
                        jsonRes = new JSONObject();
                        jsonRes.element("http_code", -1);
                        jsonRes.element("error msg", e.toString());
                        logger.error("jsonRequest: {}", jsonObject, e);
                        return jsonRes;
                    }
                })
                .thenAccept(action::apply);

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
    public static void postJsonWithTimeout(String requstId, int userId, String appId, String url, Map<String, String> headers, Object jsonObject, httpCallback<JSONObject> action, Version version, int timeout) {
        int timeOutMill = timeout * 1000;
        postJsonWithTimeoutMill(requstId, userId, appId, url, headers, jsonObject, action, version, timeOutMill);
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


    @FunctionalInterface
    public interface httpCallback<T> {
        void apply(T var1);

        default httpCallback<T> addBefore(httpCallback<? super T> callback) {
            Objects.requireNonNull(callback);
            return (T t) -> {
                callback.apply(t);
                apply(t);
            };
        }
    }

    public static void main(String[] args) {
        ExecutorService threadPoolExecutor = Executors.newCachedThreadPool();
        int k = 10000;

        while (k-- > 0) {
            threadPoolExecutor.execute(() -> {
                System.out.println(((ThreadPoolExecutor) threadPoolExecutor).getPoolSize());
                try {
                    Thread.sleep(11110000);
                    System.out.println(Thread.currentThread().getName());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        /*JSONObject jsonObject = new JSONObject();
        jsonObject.put("test", "test111");
        postJson("http://10.153.90.13:6087/xcheck/test", new HashMap<>(), jsonObject, resp -> {
            System.out.println("hello");
            return null;
        });*/
        System.out.println("=========http request ========ok");
        /*ExecutorService executorService = Executors.newSingleThreadExecutor();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1,2,10,TimeUnit.SECONDS,new LinkedBlockingDeque<>());
        threadPoolExecutor.execute(()->{
            logger.info("======线程1名称==========="+Thread.currentThread().getName());
            logger.info("======进入第一条线程了======");
            String a = " 文少";
            CompletableFuture<String> cf = new CompletableFuture<String>();
            cf.completeAsync(()->{
                logger.info("=======进入callAble1=========开始睡眠5秒");
                try{
                    Thread.sleep(5000);
                } catch (Exception e){
                    e.printStackTrace();
               }
                return "Hello,你好";
            }).thenAccept( t ->{
                System.out.println(t+a);
            });
        });
        threadPoolExecutor.execute(()->{
            logger.info("======线程2名称==========="+Thread.currentThread().getName());
            String a = " 文爷";
            logger.info("======进入第二条线程了======");
            CompletableFuture<String> cf = new CompletableFuture<String>();
            cf.supplyAsync(()->{
                logger.info("=======进入callAble1=========开始睡眠5秒");
                try{
                    Thread.sleep(5000);
                } catch (Exception e){
                    e.printStackTrace();
                }
                return "Hello,你好 ";
            }).thenAccept( t ->{
                System.out.println(t+a);
            });
        });*/
    }

}