package com.cmic.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.cmic.dao.BillDao;
import com.cmic.dao.MidAuthDao;
import com.cmic.pojo.dto.TaskUrlDto;
import com.cmic.pojo.entity.Bill;
import com.cmic.pojo.entity.CompanyTab;
import com.cmic.pojo.entity.CustomerTab;
import com.cmic.utils.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QualityCheckService {

    @Autowired
    private HttpUtil httpUtil;
    @Autowired
    private Parameters parameters;

    public boolean checkQuality(Bill bill) {
        log.info("[{}] - quality check start", bill.getId());
        try {
            String url = parameters.qcServerCreateUrl;
            CompanyTab companyTab = CompanyTabManager.companyTabMap.get(bill.getCompanyId());
            if (companyTab == null) {
                log.error("[{}] - companyTab  is null, company_id: {}", bill.getId(), bill.getCompanyId());
                return false;
            }
            CustomerTab customerTab = CustomerTabManager.customerTabMap.get(bill.getCustomerId());
            if (customerTab == null) {
                log.error("[{}] - customerTab  is null, company_id: {}", bill.getId(), bill.getCustomerId());
                return false;
            }
            Map<String, String> header = createHeader(companyTab);
            Map<String, Object> httpParams = createParams(bill, customerTab);
            log.info("[{}] - query quality service, header: {}, body: {} ", bill.getId(), JSON.toJSONString(header), JSON.toJSONString(httpParams));
            ResponseEntity<String> resp = httpUtil.sendPostRequest(url, header, httpParams);
            log.info("[{}] - query quality service, response: {}", bill.getId(), JSON.toJSONString(resp));
            if (resp.getStatusCode().is2xxSuccessful()) {
                JSONObject res = JSONObject.parseObject(resp.getBody());
                if (res.containsKey("code")) {
                    if ("0000".equals(res.getString("code"))) {
                        log.info("[{}] - call quality check service success! ", bill.getId());
                        return true;
                    }
                    log.error("[{}] - call quality check service fail! Reason : {}", bill.getId(), JSON.toJSONString(res));
                } else {
                    log.error("[{}] - call quality check service fail! Reason : {}", bill.getId(), JSON.toJSONString(res));
                }
            } else {
                log.error("[{}] - call quality check service fail! Reason : {}", bill.getId(), JSON.toJSONString(resp));
            }
        } catch (Exception e) {
            log.error("[" + bill.getId() + "] - call quality check service fail!：" + e);
        }
        return false;
    }

    public  String extractTime(String filePath) {
        // 获取纯文件名（去除路径）
        String fileName = new File(filePath).getName();
        int underscoreIdx = fileName.lastIndexOf('_');
        int dotIdx = fileName.lastIndexOf('.');
        // 确保下划线和点存在，且下划线在点之前
        if (underscoreIdx != -1 && dotIdx > underscoreIdx) {
            return fileName.substring(underscoreIdx + 1, dotIdx);
        }
        return ""; // 或抛出异常
    }

    public Map<String, String> createHeader(CompanyTab companyTab) {
        Map<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/json");

        String authorizationHeader = AuthorizationGenHelp.getCommonAuthorizationHeader(companyTab);

        //  log.info("[{}] - {}", bill.getId(), "authorizationHeader : " + authorizationHeader);
        header.put("Authorization", authorizationHeader);
        return header;
    }

    public Map<String, Object> createParams(Bill bill, CustomerTab customerTab) {
        Map<String, Object> params = new HashMap<>();
        String taskCallbackUrl = "http://" + parameters.ip + ":" + parameters.port + "/vibillcenter/task/callback";
        String subTaskCallbackUrl = "http://" + parameters.ip + ":" + parameters.port + "/vibillcenter/subtask/callback";
        String recordUrl = createRecordUrl(bill);
        String wordPackageId = createWordPackageIds(customerTab);
        Map<String, Object> userData = createUserData(bill);

        params.put("requestId", CommonUtil.generateRequestId());
        //params.put("callIdList", bill.getId());
        List<TaskUrlDto> urlList = new ArrayList<>();
        TaskUrlDto taskUrlDto = new TaskUrlDto();
        taskUrlDto.setId(bill.getId());
        taskUrlDto.setUrl(recordUrl);
        urlList.add(taskUrlDto);
        params.put("urlList", urlList);
        //params.put("customizedDetectionId", "p6rnaiv2042490010443321344");
        params.put("customizedDetectionId", "pjeu1p52049743508159467520");
        params.put("wordPackageId", wordPackageId);
        //params.put("transcription", "1");
        //params.put("transcriptionType", "0");
        //params.put("wordMatching", "");
        //params.put("wordPackageId", "");
        //params.put("summary", "");
        //params.put("sceneAnalysis", "1");
        //params.put("scriptConsistency", "");
        //params.put("scriptId", "");
        //params.put("sceneConsistency", "0");
//        params.put("forgeryDetection", "");
//        params.put("sceneRecognition", "");
        //params.put("fraudDetection", "1");
        params.put("taskCallBackUrl", taskCallbackUrl);
        params.put("subTaskCallBackUrl", subTaskCallbackUrl);
        if (!userData.isEmpty()) {
            params.put("userData", userData);
        }
        params.put("channelId", customerTab.getAppId());
        //params.put("fraudDetectionScene", "0");
        /*params.put("remark1", "");
        params.put("remark2", "");
        params.put("remark3", "");
        params.put("remark4", "");
        params.put("pornAnalysis", "");
        params.put("politicsAnalysis", "");*//*
        //params.put("violenceAnalysis", "");
*//*        params.put("gambleDrugAnalysis", "");
        params.put("asrLanguage", "");
        params.put("emotionAnalysis", "");
        params.put("asrEmotionTag", "");
        params.put("emotionChannel", "");
        params.put("regexWordMatching", "");
        params.put("regexId", "");
        params.put("dealClass", "");
        params.put("ragSearch", "");
        params.put("ragTopK", "");*/
        params.put("ragTopK", 1);
        params.put("deepSeekPromptId", "38");
        params.put("qwenPromptId","39");
        params.put("rtType", "0");
        return params;
    }

    private String createRecordUrl(Bill bill) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String prefix = "";
        String recordSource = "/internal/media/";

        String host = FileDownHostManager.fileDownHostMap.getOrDefault(parameters.location, "");
        long handleStartTimeStamp = bill.getHandleStartTime().toInstant().getEpochSecond();
        String recordUrl = host + recordSource + bill.getAppId() + "/" + bill.getMsId() + "/" + bill.getId() + "/" + handleStartTimeStamp + "/" + bill.getPfsId() + "/";
        String suffix = ".mp3";
        if (bill.getRecordType() == 1) {
            suffix = ".wav";
        }

        String fileName = prefix + bill.getId() + "_" + sdf.format(bill.getHandleStartTime()) + suffix;
        recordUrl += fileName;
        return recordUrl;
    }


    private String createWordPackageIds(CustomerTab customerTab) {
        if (StringUtils.isBlank(customerTab.getScenarioId())) {
            return "";
        }

        return Arrays.stream(customerTab.getScenarioId().split(","))
                .filter(StringUtils::isNotBlank)
                .map(scene -> EnterpriseSceneManager.enterpriseSceneMap.get(Integer.valueOf(scene)))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
    }

    private Map<String, Object> createUserData(Bill bill) {
        Map<String, Object> data = new HashMap<>();
        if (StringUtils.isNotBlank(bill.getCallerNumber())) {
            data.put("callerNumber", bill.getCallerNumber());
        }
        if (StringUtils.isNotBlank(bill.getCalleeNumber())) {
            data.put("calleeNumber", bill.getCalleeNumber());
        }
        //2026-04-15 11:31:03
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        data.put("handleStartTime", sdf.format(bill.getHandleStartTime()));
        data.put("companyId", bill.getCompanyId());
        data.put("customerId", bill.getCustomerId());
        return data;
    }
}
