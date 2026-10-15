package com.cmic.sip.param;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CallForward {

    private String callForwardNo;

    private String callForwardCause;

    private boolean isUnconditional;

    private boolean doesCallForwardContainCaller;

    private boolean doesCallForwardContainCallee;




}
