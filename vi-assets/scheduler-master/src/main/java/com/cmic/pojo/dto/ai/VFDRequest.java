package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class VFDRequest extends MQBaseDto {
    private String fileUrl;
}
