package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class IdentityRequest extends MQBaseDto {
    private String text;
    private String preset;
    private Boolean isReverseLeg;
}
