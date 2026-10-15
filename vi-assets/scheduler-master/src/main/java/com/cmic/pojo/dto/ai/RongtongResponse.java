package com.cmic.pojo.dto.ai;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class RongtongResponse  implements Serializable {
    private String jobId;
    private String resultCode;
    private String abilityType;
    private String firstLabel;
    private String secondLabel;
    private String thirdLabel;
    private String svcType;
}
