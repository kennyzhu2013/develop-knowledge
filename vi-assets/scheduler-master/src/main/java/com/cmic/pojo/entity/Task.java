package com.cmic.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "task")
public class Task {
    @Id
    private Long id;

    @Indexed(unique = true)
    private String requestId;

    private int priority = 0; //优先级：0,10

    private String processId;

    private int status = 0; //0-未开始，1-推送中，2-子任务全部已推送完成

    private int finish = 0; //0-失败，1-成功，2-部分成功

    private Map<String, Object> attributes;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;

    private int jobCount;

    public boolean needTaskCallback = true;

    public boolean needSubTaskCallback = true;

    public Long transactionId;

    private int callback = 0;   //0-未回调，1-已回调

    private boolean stream;
}
