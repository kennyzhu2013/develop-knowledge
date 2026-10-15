package com.cmic.pojo.dto;

import com.cmic.pojo.entity.Process;
import lombok.Data;

import java.util.List;

@Data
public class ProcessPageResponse {
    private long count;
    private List<Process> processList;
}
