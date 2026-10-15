package com.cmic.util;

import lombok.Getter;

public enum IndustrialResultCodeEnum {
    INDUSTRIAL_NO_QUERY_0000("0000", "未触发查询"),
    SUCCESS_1000("1000", "成功"),
    INDUSTRIAL_NODE_INVALID_2001("2001", "标识解析二级节点身份不合规"),
    INDUSTRIAL_ID_NOT_EXISTS_2002("2002", "标识编码不存在"),
    RE_INDUSTRIAL_ID_VERIFY_ERROR_2003("2003", "补充标识核验未通过(首次核验中表示核验未完成，即发出请求但最终未收到响应)"),
    PARAMETER_ERROR_3001("3001", "参数错误"),
    NETWORK_ERROR_9001("9001", "网络异常");

    @Getter
    final String code;
    final String desc;
    IndustrialResultCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

}
