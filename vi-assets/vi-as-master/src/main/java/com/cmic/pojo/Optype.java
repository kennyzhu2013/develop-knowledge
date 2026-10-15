package com.cmic.pojo;

import com.cmic.bizcontroller.controllerImpl.BaseController;
import com.cmic.bizcontroller.controllerImpl.ErrorController;

//optype枚举类，用于指示后续操作
public enum Optype {
    CloseOptype(0, ErrorController.class.getName()),
    ConnectOptype(1, BaseController.class.getName());
    // PlayVideoOptype(3, VideoRBTController.class.getName());
    private final int optype;
    private String controllerName;


    Optype(int optype, String controllerName) {
        this.optype = optype;
        this.controllerName = controllerName;
    }

    public String getControllerName() {
        return controllerName;
    }

    public int getOptype() {
        return optype;
    }

    public static Optype optypeOf(int optype) {
        for (Optype o : Optype.values()) {
            if (o.getOptype() == optype) {
                return o;
            }
        }
        return null;
    }
}
