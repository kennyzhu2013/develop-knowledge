package com.cmic.pojo.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "his_process")
public class HisProcess implements Serializable {

    @MongoId
    @JsonIgnore
    private ObjectId id;

    private String requestId;
    @Indexed
    private String processId;
    @Indexed
    private String processName;
    @Indexed
    private String xmlFileName;

    private int timeout = 10;

    private String aiModels; //AI模型，用英文逗号隔开

    private String xmlText; //XML文件内容

    private int status = 0; //0:不可用，1：可用

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
}
