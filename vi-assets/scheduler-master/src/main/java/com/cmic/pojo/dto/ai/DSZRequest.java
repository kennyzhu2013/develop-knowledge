package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class DSZRequest extends MQBaseDto {
    private String text;
}
