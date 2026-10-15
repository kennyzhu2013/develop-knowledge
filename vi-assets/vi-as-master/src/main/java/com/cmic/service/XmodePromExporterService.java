package com.cmic.service;

import com.cmic.BillNew;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

//监控指标：分机号模式呼出率=  dtmf_right_leg_num/dtmf_in_call_num
//分机号模式收号成功率 = dtmf_right_leg_num/dtmf_collect_num
//进入整个平台的呼叫数 = as_in_call_num
public class XmodePromExporterService {
    private static final Logger logger = LoggerFactory.getLogger(XmodePromExporterService.class);
    private static XmodePromExporterService instance = new XmodePromExporterService();

    private final Gauge leftlegNum = Gauge.build().name("as_left_leg_num").help("as_left_leg_num with out http error ").register();
    private final Gauge rightlegNum = Gauge.build().name("as_right_leg_num").help("as_right_leg_num out invite").register();
    private final Gauge call200okNum = Gauge.build().name("as_invite_ok_num").help("as_call_invite_200ok").register();
    private final Counter callerPlayEnd = Counter.build().name("as_caller_play_end").help("as_caller_play_end").register();
    private final Counter callerPlayStart = Counter.build().name("as_caller_play_start").help("as_caller_play_start").register();
    private final Gauge billSendNum = Gauge.build().name("as_bill_send_num").help("as_bill_send_num").labelNames("options").register();
    private final Counter billFinishState = Counter.build().name("as_bill_finishstate").help("as_bill_finishstate").labelNames("finishstate").register();
    private final Counter billFinishStateWithCompanyId = Counter.build().name("as_bill_finishstate_companyid").help("as_bill_finishstate_with_companyId").labelNames("finishstate", "companyId").register();
    private final Gauge inCallNum = Gauge.build().name("as_in_call_num").help("all in call nums").register();
    private final Gauge dtmfInCallNum = Gauge.build().name("dtmf_in_call_num").help("dtmf in call num").register();
    private final Gauge dtmfCollectNum = Gauge.build().name("dtmf_collect_num").help("dtmf collect num").register();
    private final Gauge dtmfRightLegNum = Gauge.build().name("dtmf_right_leg_num").help("dtmf right leg num").register();
    private final Counter blackListHitNum = Counter.build().name("black_list_hit_num").help("black_list_hit_num").register();
    private final Counter invalidPhoneHitNum = Counter.build().name("invalid_phone_hit_num").help("invalid_phone_hit_num").register();
    private final Counter processTimeout = Counter.build().name("process_timeout").help("process_timeout").labelNames("timeoutType").register();

    private final Counter noCallerUpdateNum = Counter.build().name("no_caller_update").help("no_caller_update").register();
    private final Gauge callRejectDueToTooMuch = Gauge.build().name("call_reject_due_to_too_much").help("call reject nums dut to too much").register();

    //爱豆业务相关指标
    private final Counter aiDouTotal = Counter.build().name("aidou_total_num").help("aidou_total_num").register();
    private final Counter aiDouSendonlySuccNum = Counter.build().name("aidou_sendonly_succ_num").help("aidou_sendonly_succ_num").register();
    private final Counter aiDouSendrecvSuccNum = Counter.build().name("aidou_sendrecv_succ_num").help("aidou_sendrecv_succ_num").register();
    private final Counter aiDouAudioPlayNum = Counter.build().name("aidou_audio_play_num").help("aidou_audio_play_num").register();
    private final Counter aiDouVideoPlayingNum = Counter.build().name("aidou_video_playing_num").help("aidou_video_playing_num").register();

    //点亮屏幕业务相关指标
    private final Counter lightScreenTotal = Counter.build().name("light_screen_total_num").help("light_screen_total_num").register();
    private final Counter lightScreenSendonlySuccNum = Counter.build().name("light_screen_sendonly_succ_num").help("light_screen_sendonly_succ_num").register();
    private final Counter lightScreenSendrecvSuccNum = Counter.build().name("light_screen_sendrecv_succ_num").help("light_screen_sendrecv_succ_num").register();
    private final Counter lightScreenAudioPlayNum = Counter.build().name("light_screen_audio_play_num").help("light_screen_audio_play_num").register();
    private final Counter lightScreenVideoPlayingNum = Counter.build().name("light_screen_video_playing_num").help("light_screen_video_playing_num").register();
    private final Counter lightScreenFailNum = Counter.build().name("light_screen_fail_num").help("light_screen_fail_num").register();

    //插播业务相关指标
    private final Counter vodTotal = Counter.build().name("vod_total_num").help("vod_total_num").register();
    private final Counter vodSendonlySuccNum = Counter.build().name("vod_sendonly_succ_num").help("vod_sendonly_succ_num").register();
    private final Counter vodSendrecvSuccNum = Counter.build().name("vod_sendrecv_succ_num").help("vod_sendrecv_succ_num").register();
    private final Counter vodAudioPlayNum = Counter.build().name("vod_audio_play_num").help("vod_audio_play_num").register();
    private final Counter vodVideoPlayingNum = Counter.build().name("vod_video_playing_num").help("vod_video_playing_num").register();
    private final Counter vodFailNum = Counter.build().name("vod_fail_num").help("vod_fail_num").register();
    public static final String CLIENT_TRANSACTION_INVITE_TIMEOUT = "client_transaction_invite_timeout";
    public static final String CLIENT_TRANSACTION_NO_INVITE_TIMEOUT = "client_transaction_non-invite_timeout";
    public static final String SERVER_TRANSACTION_TIMEOUT = "server_transaction_timeout";
    private final Counter collectReInviteCount = Counter.build().name("collect_reInvite").help("collect_reInvite").labelNames("reInviteType").register();

    public static final String REINVITE_WHILE_COLLECTING = "reinvite_while_collecting";

    public static final String REINVITE_AFTER_COLLECT = "reinvite_after_collect";

    private final Histogram dtmfCollectDelayTime = Histogram.build().name("dtmf_collect_delay_time_s").help("dtmf_collect_delay_time_s").labelNames("collectTime").register();
    private final Gauge forceToGDIcscf = Gauge.build().name("force_to_gd_icscf_num").help("the session num forced to gd icscf").register();

    private final Counter recordingZjjlIP = Counter.build().name("recording_zhejiang_jilin_ip").help("recording_zhejiang_jilin_ip").register();

    private final Counter recording3gOoBTC = Counter.build().name("recording_3gOoBTC").help("recording_3gOoBTC").register();

    private final Counter strategyAuditQueueFull = Counter.build().name("strategy_audit_queue_full").help("strategy_audit_queue_full").register();
    private final Counter strategyAuditResponseQueueFull = Counter.build().name("strategy_audit_response_queue_full").help("strategy_audit_response_queue_full").register();

    private final Counter xdtmf_mq_time_use_too_long = Counter.build().name("xdtmf_mq_time_use_too_long").help("xdtmf_mq_time_use_too_long").register();

    private final Counter rocket_queue_too_much = Counter.build().name("rocket_queue_too_much").help("rocket queue size bigger than 200").register();

    private final Counter as_downgrade = Counter.build().name("as_downgrade").help("as_downgrade").register();

    private final Counter authNumNullReject = Counter.build().name("authNum_null_reject").help("authNum_null_reject").labelNames("biz_type").register();

    private final Histogram xdtmfMqUseTime = Histogram.build().buckets(0, 2, 5, 10).name("xdtmf_mq_time_use").help("xdtmf_mq_time_use_s").labelNames("xdtmfMqTimeUse").register();

    private final Gauge xdtmfVersion = Gauge.build().name("xdtmf_version").labelNames("version", "hostname").help("xdtmf_version").register();

    private final Counter identityVerifyRejectCount = Counter.build().name("xdtmf_identity_Verify_Reject").help("xdtmf_identity_Verify_Reject").register();

    private final Gauge sipMessageQueueSize = Gauge.build().name("sip_message_queue_size").labelNames("hostname").help("sip_message_queue_size").register();

    private final Histogram dbUseTime = Histogram.build().buckets(0, 10, 100, 300, 1000).name("xdtmf_db_use_time").help("xdtmf_db_use_time_ms").labelNames("db_op_type").register();

    private final Counter dbFailCount = Counter.build().name("xdtmf_db_fail_count").help("xdtmf_db_fail_count").labelNames("fail_type").register();
    
    private final Counter writeDBLogCount =
            Counter.build().name("xdtmf_write_db_log_count").help("xdtmf_write_db_log_count").register();

    private final Counter dbTaskRejectCount = Counter.build().name("xdtmf_db_task_reject_count").help("xdtmf_db_task_reject_count").register();
    private final Counter leftLegInfo = Counter.build().name("xdtmf_left_leg_info_count").help(
            "xdtmf_left_leg_info_count").labelNames("province_id", "operator", "type").register();
    private final Counter rightLegInfo = Counter.build().name("xdtmf_right_leg_info_count").help("xdtmf_right_leg_info_count").labelNames("province_id", "operator", "type").register();
    private final Counter inviteOKInfo = Counter.build().name("xdtmf_invite_ok_info_count").help(
            "xdtmf_invite_ok_info_count").labelNames("left_province_id", "right_province_id", "left_operator",
            "right_operator", "left_type", "right_type").register();

    private final Counter businessImportant = Counter.build().name("as_important_business_count").help("as_important_business_count").labelNames("type", "message").register();
    private final Counter httpRequestError = Counter.build().name("as_http_error_count").help("as_http_error_count").labelNames("tag", "message").register();
    private final Counter industrialCode = Counter.build().name("as_industrial_code_count").help("as_industrial_code_count").labelNames("code").register();

    private final Counter noSupportCode = Counter.build().name("no_support_code").help("no support code(G729)").register();

    public static final Map<Integer, String> operNameMap = new HashMap<>();
    static {
        operNameMap.put(0, "电信");
        operNameMap.put(1, "移动");
        operNameMap.put(2, "联通");
        operNameMap.put(3, "广电");
    }

    public static XmodePromExporterService getInstance() {
        return instance;
    }

    public void incBlacklistHitNum() {
        blackListHitNum.inc();
    }


    public void incDbTaskRejectCount() {
        dbTaskRejectCount.inc();
    }

    public void incInvalidPhoneHitNum() {
        invalidPhoneHitNum.inc();
    }

    public void incDtmfRightLegNum() {
        dtmfRightLegNum.inc();
    }

    public void incDtmfInCallNum() {
        dtmfInCallNum.inc();
    }

    public void incDtmfCollectNum() {
        dtmfCollectNum.inc();
    }

    public void incInCallNum() {
        inCallNum.inc();
    }

    public void incCallRejectDueToTooMuch() {
        callRejectDueToTooMuch.inc();
    }

    public void incLeftLegNum() {
        leftlegNum.inc();
    }

    public void incRightLegNum() {
        rightlegNum.inc();
    }

    public void incAiDouTotal() {
        aiDouTotal.inc();
    }

    public void incAiDouSendonlySuccNum() {
        aiDouSendonlySuccNum.inc();
    }

    public void incAiDouSendrecvSuccNum() {
        aiDouSendrecvSuccNum.inc();
    }

    public void incAiDouAudioPlayNum() {
        aiDouAudioPlayNum.inc();
    }

    public void incAiDouVideoPlayingNum() {
        aiDouVideoPlayingNum.inc();
    }

    /*public void decAiDouVideoPlayingNum(){
        aiDouVideoPlayingNum.dec();
    }*/

    public void incLightScreenTotal() {
        lightScreenTotal.inc();
    }

    public void incLightScreenSendonlySuccNum() {
        lightScreenSendonlySuccNum.inc();
    }

    public void incLightScreenSendrecvSuccNum() {
        lightScreenSendrecvSuccNum.inc();
    }

    public void incLightScreenAudioPlayNum() {
        lightScreenAudioPlayNum.inc();
    }

    public void incLightScreenVideoPlayingNum() {
        lightScreenVideoPlayingNum.inc();
    }

   /* public void decLightScreenVideoPlayingNum(){
        lightScreenVideoPlayingNum.dec();
    }*/

    public void incLightScreenFailNum() {
        lightScreenFailNum.inc();
    }

    public void incVodTotal() {
        vodTotal.inc();
    }

    public void incVodSendonlySuccNum() {
        vodSendonlySuccNum.inc();
    }

    public void incVodSendrecvSuccNum() {
        vodSendrecvSuccNum.inc();
    }

    public void incVodAudioPlayNum() {
        vodAudioPlayNum.inc();
    }

    public void incVodVideoPlayingNum() {
        vodVideoPlayingNum.inc();
    }

    /*public void decVodVideoPlayingNum(){
        vodVideoPlayingNum.dec();
    }*/

    public void incVodFailNum() {
        vodFailNum.inc();
    }

    public void incCall200Num() {
        call200okNum.inc();
    }

    public void callerPlayEnd() {
        callerPlayEnd.inc();
    }

    public void callerPlayStart() {
        callerPlayStart.inc();
    }

    public void sendBillCenter(String label) {
        billSendNum.labels(label).inc();
    }

    public void incBillFinishState(int finishState) {
        billFinishState.labels(String.valueOf(finishState)).inc();
    }

    public void incBillFinishState(int finishState, int companyId) {
        billFinishStateWithCompanyId.labels(String.valueOf(finishState), String.valueOf(companyId)).inc();
    }

    public void incAsProcessTimeout(String timeoutType) {
        processTimeout.labels(timeoutType).inc();
    }

    public void incNoCallerUpdateNum() {
        noCallerUpdateNum.inc();
    }

    public void incCollectReInviteCount(String reInviteType) {
        collectReInviteCount.labels(reInviteType).inc();
    }

    public void setDtmfCollectDelayTime(String collectTime, long delayTimeMillis) {
        dtmfCollectDelayTime.labels(collectTime).observe((float) delayTimeMillis / 1000.00);
    }


    public void incForceToGDIcscfNum() {
        forceToGDIcscf.inc();
    }


    public void incRecordingZjjlIP() {
        recordingZjjlIP.inc();
    }

    public void incRecording3gOoBTC() {
        recording3gOoBTC.inc();
    }

    public void incStrategyAuditFullCount() {
        strategyAuditQueueFull.inc();
    }

    public void incStrategyAuditResponseFullCount() {
        strategyAuditResponseQueueFull.inc();
    }


    public void incMqTimeUseTooLong() {
        xdtmf_mq_time_use_too_long.inc();
    }

    public void incRocketQueueLarge() {
        rocket_queue_too_much.inc();
    }

    public void setDtmfMQUseTime(String mqType, long mqUseTimeMill) {
        xdtmfMqUseTime.labels(mqType).observe((float) mqUseTimeMill / 1000.00);
    }

    public void incASDowngrade() {
        as_downgrade.inc();
    }


    public void incAuthNumNullReject(String bizType) {
        authNumNullReject.labels(bizType).inc();
    }


    public void incIdentityReject() {
        identityVerifyRejectCount.inc();
    }

    public void setXdtmfVersion(String version, String hostName) {
        xdtmfVersion.labels(version, hostName).set(1);
    }

    public void setSipMessageQueueSize(String hostName, int size) {
        sipMessageQueueSize.labels(hostName).set(size);
    }

    public void setDbUseTime(String dbOpType, long useTimeMill) {
        dbUseTime.labels(dbOpType).observe((float) useTimeMill);
    }

    public void incDbFailCount(String failType) {
        dbFailCount.labels(failType).inc();
    }

    public void incWriteDbLogCount() {
        writeDBLogCount.inc();
    }

    public void incLeftLegInfoCount(int provinceId, int operator, String type) {
        leftLegInfo.labels(PhoneSegmentCacheService.getProvinceNameById(provinceId), operNameMap.get(operator), type).inc();
    }

    public void incRightLegInfoCount(int provinceId, int operator, String type) {
        rightLegInfo.labels(PhoneSegmentCacheService.getProvinceNameById(provinceId), operNameMap.get(operator), type).inc();
    }

    public void incInviteOKInfoCount(BillNew billNew) {
        Integer callInProvinceId = billNew.getCallInProvinceId();
        // Integer callerOperator = billNew.getCallerOperator();
        // String callerType = billNew.getCallerType();
        Integer callOutProvinceId = billNew.getCallOutProvinceId();
        // Integer calleeOperator = billNew.getCalleeOperator();
        // String calleeType = billNew.getCalleeType();

        // inviteOKInfo.labels(String.valueOf(callInProvinceId), String.valueOf(callOutProvinceId),
        //         String.valueOf(callerOperator), String.valueOf(calleeOperator), callerType, calleeType).inc();
    }

    public void incImportantBusiness(String type, String message) {
        businessImportant.labels(type, message).inc();
    }

    public void incHttpError(String tag, String message){
        httpRequestError.labels(tag, message).inc();
    }
    public void incIndustrialCode(String code){
        industrialCode.labels(code).inc();
    }

    public void incNoSupportCode(){
        noSupportCode.inc();
    }
}
