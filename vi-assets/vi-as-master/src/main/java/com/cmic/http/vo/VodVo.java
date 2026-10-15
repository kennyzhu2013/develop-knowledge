package com.cmic.http.vo;

import java.io.Serializable;

public class VodVo implements Serializable {
    private String code;
    private String message;

    public VodVo(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "{" +
                "\"code\":\"" + code + '\"' +
                ", \"message\":\"" + message + '\"' +
                '}';
    }
}
