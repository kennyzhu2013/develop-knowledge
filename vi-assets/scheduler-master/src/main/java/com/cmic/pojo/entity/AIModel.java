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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_model")
public class AIModel {
    @Id
    @JsonIgnore
    private ObjectId id;

    private String desc;

    @Indexed(unique = true)
    private String model;

    private List<String> requestParameters;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;

    /**http**/
    private String httpUrl;
    private List<String> responseParameters;
    private String type;
    private Map<String, Object> authentication;
}

