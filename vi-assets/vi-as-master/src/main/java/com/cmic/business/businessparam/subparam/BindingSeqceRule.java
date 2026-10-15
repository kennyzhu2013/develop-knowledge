package com.cmic.business.businessparam.subparam;

import lombok.Data;

@Data
public class BindingSeqceRule {

    /**
     * ⽆振铃⽆法接通时，顺振到下⼀个号码，
     * 例如空号、停机、关机等。该参数不传时
     * 默认为1。1：⽣效
     */
    private String callNoRinging = "1";

    /**
     * 忙呼转的情况，指被叫本身设置了【呼叫
     * 转移号码】（181信令），出现呼叫转移时
     * 顺振到下⼀个号码。该参数不传时默认为
     * 1。1：⽣效
     */
    private String callForwarded = "1";


    /**
     * 忙拒绝情况，指【被叫忙】以及【被叫拒
     * 接】时，顺振到下⼀个号码。该参数不传
     * 时默认为1。1：⽣效
     */
    private String busyReject = "1";

    /**
     * 振铃⽆响应超时顺振,传⼊数字n，表示振
     * 铃后n秒后顺振到下⼀个号码。该参数不传
     * 时默认为30（也即是30秒顺振下⼀个号
     * 码），单位秒
     */
    private int callTimeout = 30;

}
