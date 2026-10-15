package com.cmic.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QaDetail {
    private Long id;
    private String dataId;
    private String url;
    private String content;
    private Long duration;
    private String asrInfo;
    private LocalDateTime createTime;
}
