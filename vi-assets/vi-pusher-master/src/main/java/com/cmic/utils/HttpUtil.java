package com.cmic.utils;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@Slf4j
@Component
public class HttpUtil {

    @Autowired
    private RestTemplate restTemplate;

    public ResponseEntity<String> sendPostRequest(String url, Map<String, String> headerparams, Map<String, Object> bodyparams) {
        HttpHeaders headers = new HttpHeaders();
        // HttpMethod method = HttpMethod.POST;
        headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());
        if (headerparams != null) {
            for (String key : headerparams.keySet()) {
                headers.set(key, headerparams.get(key));
            }
        }
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(bodyparams, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
        return response;
    }

}
