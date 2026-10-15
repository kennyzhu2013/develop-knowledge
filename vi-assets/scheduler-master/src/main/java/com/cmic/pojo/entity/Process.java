package com.cmic.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "process")
public class Process implements Serializable {

    @Id
    @JsonIgnore
    private ObjectId id;

    @Indexed(unique = true)
    private String requestId;

    private String processId;

    private String processName;

    @Indexed(unique = true)
    private String xmlFileName;

//    @JsonIgnore
    private int timeout = 10;

    private String aiModels; //AI模型，用英文逗号隔开

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;

    private int status = 0; //0:不可用，1：可用
}
