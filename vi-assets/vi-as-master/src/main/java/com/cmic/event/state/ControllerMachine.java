package com.cmic.event.state;

import com.cmic.sip.event.SipRequestEvent;
import com.cmic.sip.event.SipResponseEvent;
import com.cmic.sip.leg.Leg;

import java.util.HashMap;

public class ControllerMachine {
    private StateController state;
    public HashMap<Integer, StateController> stateMap = new HashMap<>();

    public void processRequest(Leg var1, SipRequestEvent var2) throws Exception {
        state.processRequest(var1, var2);
    }

    public void processResponse(Leg var1, SipResponseEvent var2) throws Exception {
        state.processResponse(var1, var2);
    }

    public void switchState(int signal) throws Exception {
        if (state != null) {
            state.switchOutState();
        }
        if (stateMap.get(signal) != null) {
            setState(stateMap.get(signal));
            state.switchInState();
        } else {
            throw new Exception("no such state : signal is " + signal);
        }


    }

    public void setState(StateController state) {
        this.state = state;
    }

    public StateController getState() {
        return state;
    }

    public StateController getStateBySignal(int signal) {
        return stateMap.get(signal);
    }

    public void buildStateMap(int optype, StateController stateController) {
        stateMap.put(optype, stateController);
    }
}
