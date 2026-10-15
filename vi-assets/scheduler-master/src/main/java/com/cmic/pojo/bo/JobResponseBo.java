package com.cmic.pojo.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponseBo {
    private boolean asrStatus;
    private String asrText;
    private long audioDuration;
    private boolean keywordStatus;
    private List<KeywordBo> keywords;
    private boolean scriptStatus;
    private List<ScriptBo> scripts;
    private boolean keywordAnalysisStatus;

    private boolean behaviorStatus;
}
