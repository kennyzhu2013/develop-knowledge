package com.cmic.pojo.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class CommonResult<T> implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final String SUCCESS_CODE = "0000";
	private static final String DEFAULT_SUCCESS_MSG = "成功";

	private static final String ERROR_CODE = "9999";
	private static final String DEFAULT_ERROR_MSG = "失败";

	private String code;
	private T data;
	private String message;

	public CommonResult() {}

	public CommonResult(String code, T data, String message) {
		this.code = code;
		this.data = data;
		this.message = message;
	}

	/**
	 * 判断是否成功
	 * @return
	 */
	public boolean isSuccess() {
		return this.code == SUCCESS_CODE;
	}

	public static <T> CommonResult<T> success() {
		return new CommonResult<>(SUCCESS_CODE, null, DEFAULT_SUCCESS_MSG);
	}

	public static <T> CommonResult<T> success(T data) {
		return new CommonResult<>(SUCCESS_CODE, data, DEFAULT_SUCCESS_MSG);
	}

	public static <T> CommonResult<T> success(String msg) {
		return new CommonResult<>(SUCCESS_CODE, null, msg);
	}

	public static <T> CommonResult<T> success(T data, String msg) {
		return new CommonResult<>(SUCCESS_CODE, data, msg);
	}

	public static <T> CommonResult<T> success(String code, String msg) {
		return new CommonResult<>(code, null, msg);
	}

	public static <T> CommonResult<T> success(String code, T data, String msg) {
		return new CommonResult<>(code, data, msg);
	}

	public static <T> CommonResult<T> error(T data) {
		return new CommonResult<>(ERROR_CODE, data, DEFAULT_ERROR_MSG);
	}

	public static <T> CommonResult<T> error(String msg) {
		return new CommonResult<>(ERROR_CODE, null, msg);
	}

	public static <T> CommonResult<T> error(T data, String msg) {
		return new CommonResult<>(ERROR_CODE, data, msg);
	}

	public static <T> CommonResult<T> error(String code, String msg) {
		return new CommonResult<>(code, null, msg);
	}

	public static <T> CommonResult<T> error(String code, T data, String msg) {
		return new CommonResult<>(code, data, msg);
	}

}
