package com.cmic.business.businessparam.subparam;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Data
@ToString
public class BindDetail {

    private String bindId;
    /**
     * 中间号绑定模式，可选枚举有:
     * 0：AXB模式
     * 1：AXYB模式
     * 2：GXB模式
     * 3：GXYB模式
     */
    private String bindMode;

    /**
     * 号码A，不带86、0086，区号补0格式，例如13803111234、01082325588
     * 当且仅当BindMode为AXB、AXYB模式必选
     */
    private String telA;

    /**
     * 号码B，不带86、0086，区号补0格式，例如13803111234、01082325588
     */
    private String telB;

    /**
     * 700中间号X
     */
    private String telX;

    /**
     * 700中间号Y，当且仅当BindMode为GXYB，AXYB模式时必选
     */
    private String telY;

    /**
     * 号码组G的电话号码，多个号码/号码段之间用英文逗号分隔。
     * 当BindMode为GXB、GXYB时必选。
     */
    private String telG;

    /**
     * 超时时间。YYYYMMDDHHMMSS格式。
     */
    private String expTime;

    private String bindTime;



}
