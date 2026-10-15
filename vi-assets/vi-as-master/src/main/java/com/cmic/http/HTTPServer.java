package com.cmic.http;

import com.cmic.http.handler.*;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.*;

public class HTTPServer {
    static class DaemonThreadFactory implements ThreadFactory {
        private ThreadFactory delegate;
        private final boolean daemon;

        DaemonThreadFactory(ThreadFactory delegate, boolean daemon) {
            this.delegate = delegate;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = delegate.newThread(r);
            t.setDaemon(daemon);
            return t;
        }

        static ThreadFactory defaultThreadFactory(boolean daemon) {
            return new DaemonThreadFactory(Executors.defaultThreadFactory(), daemon);
        }
    }

    protected HttpServer server;
    protected ExecutorService executorService;


    protected String host = "";
    protected int port = 6060;
    protected int threads = 10;

    private final Map<String, HttpHandler> hashmap = new ConcurrentHashMap<>();

    public HTTPServer(String host, int port, int threads) throws IOException {
        this.host = host;
        this.port = port;
        this.threads = threads;
    }

    /*** 添加自定义接口处理 **/
    public void addHandler(String path, HttpHandler handler) {
        hashmap.put(path, handler);
    }

    public void startServer() throws IOException {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(this.host, this.port), 3);

        /* 系统内置handler */
        /** 监控exporter **/
        server.createContext("/metrics", new MetricHandler(CollectorRegistry.defaultRegistry));
        /** 运维操作接口 **/
        server.createContext("/ops", new OpsHandler());
        /** AS与xmedia交互接口 **/
        server.createContext("/media", new MediaHandler());

        server.createContext("/hook", new HookHandler());
        server.createContext("/forcemediacodec", new ForceMediaCodecHandler());


        /* 用户自定义handler */
        for (String key : hashmap.keySet()) {
            server.createContext(key, hashmap.get(key));
        }

        executorService = Executors.newFixedThreadPool(this.threads, DaemonThreadFactory.defaultThreadFactory(false));
        server.setExecutor(executorService);
        start(false);
    }


    /**
     * Start a HTTP server by making sure that its background thread inherit proper daemon flag.
     */
    private void start(boolean daemon) {
        if (daemon == Thread.currentThread().isDaemon()) {
            server.start();
        } else {
            FutureTask<Void> startTask = new FutureTask<Void>(new Runnable() {
                @Override
                public void run() {
                    server.start();
                }
            }, null);
            DaemonThreadFactory.defaultThreadFactory(daemon).newThread(startTask).start();
            try {
                startTask.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Unexpected exception on starting HTTPSever", e);
            } catch (InterruptedException e) {
                // This is possible only if the current tread has been interrupted,
                // but in real use cases this should not happen.
                // In any case, there is nothing to do, except to propagate interrupted flag.
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        server.stop(0);
        executorService.shutdown(); // Free any (parked/idle) threads in pool
    }
}

