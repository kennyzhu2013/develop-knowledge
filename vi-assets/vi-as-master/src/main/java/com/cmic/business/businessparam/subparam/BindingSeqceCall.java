package com.cmic.business.businessparam.subparam;

import lombok.Data;

@Data
public class BindingSeqceCall {

    private String sequenceCalledNo;

    /**
     * 接通前主叫放音
     */
    private String sequenceCallNoPlayCode;

    /**
     * 接通后主被叫放音
     */
    private String sequenceCalledPlayCode;

    private String sequenceDisplayNo;

    private String sequenceData;

    private String sequenceBillSeqNo;

}
