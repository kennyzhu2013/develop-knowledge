package com.cmic.pojo.dto;

import com.cmic.pojo.bo.AIModelWithType;
import lombok.Data;

import java.util.List;
@Data
public class AiModelPageResponse {
    private long count;
    private List<AIModelWithType> aiModels;
}
