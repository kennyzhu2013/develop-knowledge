package com.cmic.recording;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CallAbnormalInfo {
    private boolean doesCalleeContain3gOoBTC;
    private boolean doesCalleeContainZjjlIP;
}
