package com.cmic.pojo.dto;

import com.cmic.pojo.bo.KeywordBo;
import com.cmic.pojo.bo.ScriptBo;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Data
public class RequestExtParameter implements Serializable {
    /**
     * 语音转写类型（默认0）（一般情况下，主叫在左声道，被叫在右声道）
     * 0 - 左声道转写
     * 1 - 右声道转写
     * 2 - 左右双声道转写
     */
    @Pattern(regexp = "[012]", message = "illegal transcriptionType")
    public String transcriptionType;

    /**
     * 关键词对比使用的词库
     */
    public List<KeywordBo> wordPackage;

//    /**
//     * 预期场景（默认""），当scenarioAnalysis字段为1时才会使用。
//     * 1-交通出行,2-配送服务,3-房产销售,4-招聘,5-教育,6-生活服务,7-其他
//     */
//    @Pattern(regexp = "[1234567]", message = "illegal expectedScenario")
//    public String expectedScenario;

    /**
     * 话术比对时用到的话术
     */
    public List<ScriptBo> scripts;

    /**
     * Tfidf的事务 ID
     */
    public String transactionId;

    /**
     * 客户端透传数据
     */
    public String data;
}
