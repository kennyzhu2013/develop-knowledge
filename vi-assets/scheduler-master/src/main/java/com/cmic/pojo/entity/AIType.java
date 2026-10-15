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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "ai_type")
public class AIType {
    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String nickName;

    private String comment;

    @JsonIgnore
    private LocalDateTime createTime;

    @JsonIgnore
    private LocalDateTime updateTime;
}
