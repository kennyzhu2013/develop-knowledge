package com.cmic.pojo.dto;

public enum ErrorCode {
    SUCCESS("0000", "成功"),
    FAILED("1001", "失败"),
    PARAMETER("1002", "参数错误"),
    TIMEOUT("9998", "超时"),
    ERROR("9999", "异常"),
    ;

    private String code;
    private String message;
    ErrorCode(String code, String message){
        this.code = code;
        this.message = message;
    }

    public String getCode(){
        return code;
    }

    public String getMessage(){
        return message;
    }

    public boolean equals(String code){
        if(this.code.equalsIgnoreCase(code)){
            return true;
        }
        return false;
    }

    public static ErrorCode getErrorCode(String code){
        for(ErrorCode errorCode: ErrorCode.values()){
            if(errorCode.code.equalsIgnoreCase(code)){
                return errorCode;
            }
        }
        return null;
    }
}
