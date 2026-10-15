package com.cmic.dao;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class BillNewDo {
    /**
     * 话单id
     */
    private String id;
    /**
     * 一级移动id
     */
    private Integer mobile_id;
    /**
     * 移动id
     */
    private Integer channel_id;

    /**
     * 企业id
     */
    private Integer company_id;

    /**
     * 终端客户id
     */
    private Integer customer_id;

    /**
     * customer对应的app_id
     */
    private String app_id;

    /**
     * 呼叫触发节点
     */
    private Integer location;

    /**
     * AS节点id
     */
    private Integer as_id;

    /**
     * MS节点id
     */
    private Integer ms_id;

    /**
     * 原始主叫号码
     */
    private String original_caller_number;

    /**
     * 主叫号码
     */
    private String caller_number;

    /**
     * 原始被叫号码
     */
    private String original_callee_number;

    /**
     * 被叫号码
     */
    private String callee_number;

    /**
     * 被叫号码
     */
    private String biz_number;

    /**
     * AS开始处理时间
     */
    private Timestamp handle_start_time;

    /**
     * AS结束处理时间
     */
    private Timestamp handle_end_time;

    /**
     * 接续被叫时间
     */
    private Timestamp call_forward_time;

    /**
     * 被叫首次response的时间
     */
    private Timestamp first_resp_time;

    /**
     * 振铃时间
     */
    private Timestamp ring_time;

    /**
     * 通话开始时间
     */
    private Timestamp call_start_time;

    /**
     * 通话结束时间
     */
    private Timestamp call_end_time;

    /**
     * 通话时长 (秒)
     */
    private Integer call_duration_s;

    /**
     * 通话时长 (分)
     */
    private Integer call_duration_m;

    /**
     * 录音时长 (秒)
     */
    private Integer file_duration;

    /**
     * 左腿录音
     */
    private Integer left_file_length;

    /**
     * 右腿录音
     */
    private Integer right_file_length;

    /**
     * callstate: 信令协商阶段: 1-180 2-200inv 3-181 4-reinvite 5-视频 6-ack
     */
    private Integer call_state;

    /**
     * 结束状态 0 平台 1主叫 2被叫
     */
    private Integer finish_type;

    /**
     * 话单状态
     */
    private Integer finish_state;

    /**
     * 是否话单推送成功
     */
    private Integer is_bill_ok;

    /**
     * 是否录音推送成功
     */
    private Integer is_record_ok;

    /**
     * 录音格式 1:wav 2:mp3
     */
    private Integer record_type;

    /**
     * Q.850 cause 的值
     */
    private Integer q850;

    /**
     * 主叫最后协商的media direction: 1-none, 2-sendrecv, 3-sendonly, 4-recvonly, 5-inactive, 6-invalid
     */
    private Integer media_l_direction;

    /**
     * 被叫最后协商的media direction: 1-none, 2-sendrecv, 3-sendonly, 4-recvonly, 5-inactive, 6-invalid
     */
    private Integer media_r_direction;

    /**
     * nfs的id
     */
    private Integer pfs_id;

    /**
     * 账户类型 1：测试账户 2：联调账户 3：生产账户
     */
    private Integer account_type;

    /**
     * 话单状态附加原因：1 - 默认值 1 - 呼转拦截通话 2 - 主叫黑名单拦截通话 3 - 被叫黑名单拦截通话 4 - 被叫非白名单拦截通话 5 - 呼叫频次异常拦截通话 6 - 虚商被叫拦截通话
     */
    private Integer append_reason;

    /**
     * 呼叫触发省份id
     */
    private Integer call_in_province_id;

    /**
     * 被叫省份id
     */
    private Integer call_out_province_id;

    /**
     * 700号码区号
     */
    private String call_in_700_area_code;

    /**
     * 呼入区号
     */
    private String call_in_area_code;

    /**
     * 呼出区号
     */
    private String call_out_area_code;
}
