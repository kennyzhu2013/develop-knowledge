package com.cmic;

import com.cmic.service.PhoneSegmentCacheService;
import com.cmic.service.XmodePromExporterService;
import com.cmic.util.DtmfLogger;
import com.cmic.util.PhoneNumUtil;
import com.cmic.util.SipUtil;
import com.dcc.config.util.PhoneUtil;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;

import java.sql.Timestamp;

/**
 * 语音质检话单表
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-15
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class BillNew {

    /**
     * 话单id
     */
    @NonNull
    private String id;
    /**
     * 一级移动id
     */
    private int mobileId;

    /**
     * 二级移动id
     */
    private int channelId;

    /**
     * 企业id
     */
    private int companyId;

    /**
     * 终端客户id
     */
    private int customerId;

    /**
     * customer对应的app_id
     */
    private String appId;

    /**
     * 呼叫触发节点
     */
    private int location;

    /**
     * AS节点id
     */
    private int asId;

    /**
     * media节点id
     */
    private int msId;

    /**
     * 原始主叫号码
     */
    private String originalCallerNumber;

    /**
     * 主叫号码
     */
    private String callerNumber;

    /**
     * 原始被叫号码
     */
    private String originalCalleeNumber;

    /**
     * 被叫号码
     */
    private String calleeNumber;

    /**
     * 业务号码
     */
    private String bizNumber;

    /**
     * AS开始处理时间
     */
    private Timestamp handleStartTime;

    /**
     * AS结束处理时间
     */
    private Timestamp handleEndTime;

    /**
     * 接续被叫时间
     */
    private Timestamp callForwardTime;

    /**
     * 振铃时间
     */
    private Timestamp ringTime;

    /**
     * 通话开始时间
     */
    private Timestamp callStartTime;

    /**
     * 通话结束时间
     */
    private Timestamp callEndTime;

    /**
     * 通话时长（秒）
     */
    private int callDurationS;

    /**
     * 通话时长（分）
     */
    private int callDurationM;

    /**
     * 录音时长（秒）
     */
    private int fileDuration;

    /**
     * 左腿录音
     */
    private int leftFileLength;

    /**
     * 右腿录音
     */
    private int rightFileLength;

    /**
     * callstate: 信令协商阶段: 1-180 2-200inv 3-181 4-reinvite 5-视频 6-ack
     */
    private int callState = 0;

    /**
     * 结束状态 0 平台 1主叫 2被叫
     */
    private int finishType;

    /**
     * 话单状态
     */
    private int finishState;

    /**
     * 是否话单推送成功
     */
    private int isBillOk;

    /**
     * 是否录音推送成功
     */
    private int isRecordOk;

    /**
     * 透传数据
     */
    private String data;

    /**
     * 录音格式 1:wav 2:mp3
     */
    private int recordType;

    /**
     * 媒体协商次数
     */
    private int negotiateTimes;

    /**
     * 被叫首次response的时间
     */
    private Timestamp firstRespTime;

    /**
     * Q.850 cause 的值
     */
    private int q850;

    /**
     * 主叫最后协商的media direction: 1-none, 2-sendrecv, 3-sendonly, 4-recvonly, 5-inactive, 6-invalid
     */
    private int mediaLDirection;

    /**
     * 被叫最后协商的media direction: 1-none, 2-sendrecv, 3-sendonly, 4-recvonly, 5-inactive, 6-invalid
     */
    private int mediaRDirection;

    /**
     * pfs的id
     */
    private int storeId;

    /**
     * 账户类型 1：测试账户 2：联调账户 3：生产账户
     */
    private int accountType;

    /**
     * 话单状态附加原因：-1 - 默认值, 1 - 呼转拦截通话, 2 - 主叫黑名单拦截通话, 3 - 被叫黑名单拦截通话, 4 - 被叫非白名单拦截通话, 5 - 呼叫频次异常拦截通话, 6 - 虚商被叫拦截通话
     */
    private int appendReason;

    /**
     * 呼叫触发省份id
     */
    private int callInProvinceId;

    /**
     * 被叫省份id
     */
    private int callOutProvinceId;

    /**
     * 700呼入区号
     */
    private String callIn700AreaCode;

    /**
     * 呼入区号
     */
    private String callInAreaCode;

    /**
     * 呼出区号
     */
    private String callOutAreaCode;

    private String originToCallee;

    public void setLeftLegInfo(SIPRequest initRequest) {
        // 号码类型，手机号-mobile、固话-landline、特服号码-special
        String callerType;
        try {
            // 由初始INVITE的VIA头域中的icscf的地址得到的省会城市的区号
            String homeAreaCodeFromFirstInvite = SipUtil.getHomeAreaCodeFromFirstInvite(initRequest);
            // 主叫为手机号码，专线质检一般不存在
            if (PhoneNumUtil.isValidMobilePhone(callerNumber, "")) {
                callerType = "mobile";
                DtmfLogger.info(id, "setLeftLegInfo caller num is valid mobile phone");
                // 如果本地有缓存触发的icscf地址信息，以此为准
                if (StringUtils.isNotBlank(homeAreaCodeFromFirstInvite)) {
                    callInProvinceId = PhoneSegmentCacheService.getProvinceIdFromAreaCode(homeAreaCodeFromFirstInvite);
                    callInAreaCode = homeAreaCodeFromFirstInvite;
                } else {
                    // 兜底查询号段归属信息表
                    PhoneSegmentCacheService.PhoneSegment phoneSegment = PhoneSegmentCacheService.getSegmentDtoFromPhone(callerNumber);
                    if (phoneSegment == null) {
                        DtmfLogger.warn("[{}]get phoneSegment is null by caller num:{}", id, callerNumber);
                    } else {
                        this.callInProvinceId = phoneSegment.getProvinceId();
                        callInAreaCode = phoneSegment.getAreaCode();
                    }
                }
            } else {
                String areaCode = PhoneNumUtil.getFixPhoneAreaCode(callerNumber);
                if (StringUtils.isNotBlank(areaCode)) {
                    DtmfLogger.info(id, "setLeftLegInfo caller num {} is valid fix phone", callerNumber);
                    callerType = "landline";
                } else {
                    DtmfLogger.info(id, "setLeftLegInfo caller num {} is special num", callerNumber);
                    callerType = "special";
                }
                // 暂时先以网络信息定位优先
                if (StringUtils.isNotBlank(homeAreaCodeFromFirstInvite)) {
                    callInProvinceId = PhoneSegmentCacheService.getProvinceIdFromAreaCode(homeAreaCodeFromFirstInvite);
                    callInAreaCode = homeAreaCodeFromFirstInvite;
                } else {
                    callInProvinceId = PhoneSegmentCacheService.getProvinceIdFromAreaCode(areaCode);
                    callInAreaCode = areaCode;
                }
            }
            // 运营商默认移动，主叫号码类型默认是专线固话或者签约工作号签约点的用户
            XmodePromExporterService.getInstance().incLeftLegInfoCount(callInProvinceId, 1, callerType);
        } catch (Exception e) {
            DtmfLogger.error(id, "setLeftLegInfo failed", e);
        }
    }

    public void setRightLegInfo(String calleeNumber) {
        // 号码类型，手机号-mobile、固话-landline、特服号码-special
        String calleeType;
        int calleeOperator = 0;
        try {
            if (PhoneNumUtil.isValidMobilePhone(calleeNumber, "")) {
                calleeType = "mobile";
                DtmfLogger.info(id, "setCalleeNum callee num is valid mobile phone");
                PhoneSegmentCacheService.PhoneSegment phoneSegment = PhoneSegmentCacheService.getSegmentDtoFromPhone(calleeNumber);
                if (phoneSegment == null) {
                    DtmfLogger.info("[{}]get phoneSegment is null by callee num:{}", id, calleeNumber);
                } else {
                    this.callOutProvinceId = phoneSegment.getProvinceId();
                    this.callOutAreaCode = phoneSegment.getAreaCode();
                    calleeOperator = phoneSegment.getOperator();
                }
            } else {
                String areaCode = PhoneNumUtil.getFixPhoneAreaCode(calleeNumber);
                if (StringUtils.isNotBlank(areaCode)) {
                    DtmfLogger.info(id, "setCalleeNum callee num is valid fix phone");
                    calleeType = "landline";
                    callInProvinceId = PhoneSegmentCacheService.getProvinceIdFromAreaCode(areaCode);
                    callOutAreaCode= areaCode;
                } else {
                    DtmfLogger.info(id, "setCalleeNum callee num is special num");
                    calleeType = "special";
                }
            }
            XmodePromExporterService.getInstance().incRightLegInfoCount(callOutProvinceId, calleeOperator, calleeType);
        } catch (Exception e) {
            DtmfLogger.error(id, "setCalleeNum failed", e);
        }
    }

    public void setCallEndTime(Timestamp callEndTime) {
        this.callEndTime = callEndTime;
        if(this.callEndTime != null && callStartTime != null) {
            int callDurationMs = (int) (callEndTime.getTime() - callStartTime.getTime());
            // 兼容一下很极端的并发情况
            if(callDurationMs <= 0) {
                callDurationMs = 1;
            }
            this.callDurationS = (int) Math.ceil(callDurationMs * 1.0 / 1000);
            this.callDurationM = (int) Math.ceil(callDurationMs * 1.0 / 60000);
        }
    }
}
