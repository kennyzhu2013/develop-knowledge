package com.cmic.utils;

import com.alibaba.fastjson2.JSONObject;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLSession;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
public class HttpSender {


    @Autowired
    private HttpClientFactory httpClientFactory;
    @Autowired
    private HttpClient httpClient;


    private Map<String, HttpClient> httpClientMap = new ConcurrentHashMap<>();

    public final NullResponse NULL_RESPONSE = new NullResponse();


    private HttpClient getHttpClient(String node) throws Exception {
        if (StringUtil.isNullOrEmpty(node)) {
            throw new Exception("httpproxy host can not be null");
        }
        if (httpClientMap.containsKey(node)) {
            return httpClientMap.get(node);
        } else {
            String[] hostPort = node.replace("http://", "").split(":");
            if (hostPort.length >= 2) {
                String host = hostPort[0];
                int port = Integer.parseInt(hostPort[1]);
                HttpClient client = httpClientFactory.createHttpClient(new InetSocketAddress(host, port));
                httpClientMap.put(node, client);
                return client;
            } else {
                throw new Exception("unexpected host :" + node);
            }
        }
    }

    //正向代理，同步方式发送http get请求
    public HttpResponse<String> sendGetRequest(String url, String proxy, Map<String, String> header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpClient client = getHttpClient(proxy);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //非代理，同步方式发送http get请求
    public HttpResponse<String> sendGetRequest(String url, Map<String, String> header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //正向代理，同步方式发送http post请求
    public HttpResponse<String> sendPostRequest(String url, String proxy, Map<String, String> header, Map<String, Object> body) throws Exception {
        JSONObject json = new JSONObject(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()));
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpClient client = getHttpClient(proxy);
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //非代理，同步方式发送http post请求
    public HttpResponse<String> sendPostRequest(String url, Map<String, String> header, Map<String, Object> body) throws Exception {
        JSONObject json = new JSONObject(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()));
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //正向代理，异步方式发送http get请求
    public CompletableFuture<HttpResponse<String>> sendGetRequestAsync(String url, String proxy, Map<String, String> header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpClient client = getHttpClient(proxy);
        CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //非代理，异步方式发送http get请求
    public CompletableFuture<HttpResponse<String>> sendGetRequestAsync(String url, Map<String, String> header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }


    //正向代理，异步方式发送http post请求
    public CompletableFuture<HttpResponse<String>> sendPostRequestAsync(String url, String proxy, Map<String, String> header, Map<String, Object> body, boolean isSms) throws Exception {
        JSONObject json = new JSONObject(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()));
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        HttpClient client = getHttpClient(proxy);
        String bodyJson = JSONObject.toJSONString(body);
        if (!isSms) {
            log.info("[{}] - request data: {}, request url : {}", header.get("Zy-CallId"), bodyJson, url);
        }
        CompletableFuture<HttpResponse<String>> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    //非代理-改，异步方式发送http post请求
//    public CompletableFuture<HttpResponse<String>> sendPostRequestAsync(String url,String proxy,Map<String,String> header, Map<String,Object > body , boolean isSms) throws Exception {
//        header.put("zy-url",url);
//        url = proxy + "/httpproxy";
//        JSONObject json = new JSONObject(body);
//        HttpRequest.Builder builder = HttpRequest.newBuilder()
//                .uri(URI.create(url))
//                .timeout(Duration.ofSeconds(10))
//                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
//                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
//                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()));
//        if(header != null && header.size() > 0){
//            for(String key : header.keySet()){
//                builder.header(key,header.get(key));
//            }
//        }
//        HttpRequest request = builder.build();
//        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request,HttpResponse.BodyHandlers.ofString());
//        String bodyJson = JSONObject.toJSONString(body);
//        if(!isSms){
//            log.info("[{}] - request data: {}, request url : {}", header.get("Zy-CallId"), bodyJson, url);
//        }
//        return response;
//    }


    //非代理，异步方式发送http post请求
    public CompletableFuture<HttpResponse<String>> sendPostRequestAsync(String url, Map<String, String> header, Map<String, Object> body) throws Exception {
        JSONObject json = new JSONObject(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(json.toJSONString()));
        if (header != null && header.size() > 0) {
            for (String key : header.keySet()) {
                builder.header(key, header.get(key));
            }
        }
        HttpRequest request = builder.build();
        CompletableFuture<HttpResponse<String>> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }

    public static class NullResponse implements HttpResponse {

        @Override
        public int statusCode() {
            return -1;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return null;
        }

        @Override
        public Object body() {
            return null;
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
        public HttpClient.Version version() {
            return null;
        }
    }
}
