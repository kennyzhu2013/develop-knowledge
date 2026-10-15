package com.cmic.http.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

public class MetricHandler implements HttpHandler {
    private static class LocalByteArray extends ThreadLocal<ByteArrayOutputStream> {
        @Override
        protected ByteArrayOutputStream initialValue() {
            return new ByteArrayOutputStream(1 << 20);
        }
    }

    private CollectorRegistry registry;
    private final LocalByteArray response = new LocalByteArray();

    public MetricHandler(CollectorRegistry registry) {
        this.registry = registry;
    }


    @Override
    public void handle(HttpExchange t) throws IOException {
        String query = t.getRequestURI().getRawQuery();

        ByteArrayOutputStream response = this.response.get();
        response.reset();
        OutputStreamWriter osw = new OutputStreamWriter(response);
        TextFormat.write004(osw, registry.filteredMetricFamilySamples(parseQuery(query)));
        osw.flush();

        osw.close();
        response.flush();
        response.close();

        t.getResponseHeaders().set("Content-Type", TextFormat.CONTENT_TYPE_004);
        if (shouldUseCompression(t)) {
            t.getResponseHeaders().set("Content-Encoding", "gzip");
            t.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            final GZIPOutputStream os = new GZIPOutputStream(t.getResponseBody());
            response.writeTo(os);
            os.close();
        } else {
            t.getResponseHeaders().set("Content-Length", String.valueOf(response.size()));
            t.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.size());
            response.writeTo(t.getResponseBody());
        }
        t.close();
    }


    protected static boolean shouldUseCompression(HttpExchange exchange) {
        List<String> encodingHeaders = exchange.getRequestHeaders().get("Accept-Encoding");
        if (encodingHeaders == null) {
            return false;
        }

        for (String encodingHeader : encodingHeaders) {
            String[] encodings = encodingHeader.split(",");
            for (String encoding : encodings) {
                if (encoding.trim().toLowerCase().equals("gzip")) {
                    return true;
                }
            }
        }
        return false;
    }


    protected static Set<String> parseQuery(String query) throws IOException {
        Set<String> names = new HashSet<String>();
        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1 && URLDecoder.decode(pair.substring(0, idx), "UTF-8").equals("name[]")) {
                    names.add(URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
                }
            }
        }
        return names;
    }
}
