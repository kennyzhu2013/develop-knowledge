package com.cmic.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "model_meta")
@CompoundIndex(name = "uk_model_version", def = "{'model': 1, 'version': 1}", unique = true)
public class ModelMeta {
    @Id
    @JsonIgnore
    private ObjectId id;

    private String model;

    private String version;

    private String desc;

    private String type;

    /** 是否为默认模型 */
    private Boolean isDefault;

    /**http**/
    private String httpUrl;
    private Map<String, Object> authentication;
    private List<String> requestParameters;
    private List<String> responseParameters;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;

    private Integer maxConcurrency;
}
