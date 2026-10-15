package com.cmic.pojo.bo;

import lombok.Data;

import java.io.Serializable;

@Data
public class TaskMqBo implements Serializable {
    private Long taskId;
    private int finish;
}
