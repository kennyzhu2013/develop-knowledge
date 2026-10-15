package com.cmic.pojo.dto;

import com.cmic.pojo.entity.HisProcess;
import lombok.Data;

import java.util.List;

@Data
public class HisProcessPageResponse {
    private long count;
    private List<HisProcess> hisProcessList;
}
