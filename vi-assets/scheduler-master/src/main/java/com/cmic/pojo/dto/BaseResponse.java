package com.cmic.pojo.dto;

import lombok.Data;

@Data
public class BaseResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> BaseResponse constructResponse(ErrorCode errorCode, T t){
        return constructResponse(errorCode.getCode(), errorCode.getMessage(), t);
    }

    public static <T> BaseResponse constructResponse(ErrorCode errorCode){
        return constructResponse(errorCode, null);
    }

    public static <T> BaseResponse constructResponse(String code, String message){
        return constructResponse(code, message, null);
    }

    public static <T> BaseResponse constructResponse(String code, String message, T t){
        BaseResponse responseBody = new BaseResponse();
        responseBody.setCode(code);
        responseBody.setMessage(message);
        responseBody.setData(t);

        return responseBody;
    }
}
