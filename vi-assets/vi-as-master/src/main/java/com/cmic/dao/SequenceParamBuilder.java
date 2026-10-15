package com.cmic.dao;

import com.cmic.pojo.SequenceParamsBill;

public class SequenceParamBuilder {
    public static SequenceParamDo buildInitialInsert(SequenceParamsBill sequenceParamsBill) {
        SequenceParamDo sequenceParamDo = new SequenceParamDo();
        sequenceParamDo.setId(sequenceParamsBill.getBillId());
        sequenceParamDo.setParam_string(sequenceParamsBill.toBillString());
        return sequenceParamDo;
    }
}
