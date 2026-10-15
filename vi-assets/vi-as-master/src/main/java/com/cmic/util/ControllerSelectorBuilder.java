package com.cmic.util;

import com.cmic.pojo.Optype;

import java.util.HashMap;

public class ControllerSelectorBuilder {

    public String optype_0;
    public String optype_1;
    public String optype_2;

    public ControllerSelectorBuilder() {

    }

    public HashMap<Optype, String> buildControllerSelector() {
        HashMap<Optype, String> hashMap = new HashMap<>();
        hashMap.put(Optype.CloseOptype, optype_0);
        hashMap.put(Optype.ConnectOptype, optype_1);
        return hashMap;
    }

    public ControllerSelectorBuilder optype_0(String optype_0) {
        this.optype_0 = optype_0;
        return this;
    }

    public ControllerSelectorBuilder optype_1(String optype_1) {
        this.optype_1 = optype_1;
        return this;
    }

    public ControllerSelectorBuilder optype_2(String optype_2) {
        this.optype_2 = optype_2;
        return this;
    }

}
