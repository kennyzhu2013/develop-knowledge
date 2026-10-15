package com.cmic.pojo.bo;

import lombok.Data;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.List;
import java.util.Map;
@Data
public class AIModelWithType {
    private String desc;

    @Indexed(unique = true)
    private String model;

    private List<String> requestParameters;

    /**http**/
    private String httpUrl;
    private List<String> responseParameters;
    private String type;
    private String aiTypeId;
    private Map<String, Object> authentication;


}
