package com.cmic.pojo;

import com.cmic.state.FinishState;

import java.util.HashSet;

public class UserState {
    public static final int USER_BUSY = 486;
    public static final int USER_NOT_RESPONSE = 408;
    public static final int USER_UNACCESS = 503;
    public static final int USER_ALL_FORWARD = 302;

    public static HashSet<Integer> userStateSet = new HashSet<>();

    static {
        userStateSet.add(USER_BUSY);
        userStateSet.add(USER_NOT_RESPONSE);
        userStateSet.add(USER_UNACCESS);
        userStateSet.add(USER_ALL_FORWARD);
    }

    public static int getUserState(int cause, boolean isSend180, int statusCode) {
        if (userStateSet.contains(statusCode)) {
            return statusCode;
        } else if (cause != 0) {
            if (FinishState.causeToFinishState(cause, isSend180, statusCode) != 0) {
                Integer causeInteger = FinishState.getCauseFinishState().get(cause);
                Integer statusCodeInteger = FinishState.getCauseFinishState().get(statusCode);
                if ((causeInteger != null && causeInteger == FinishState.RIGHT_LEG_REJECT) || (statusCodeInteger != null && statusCodeInteger == FinishState.RIGHT_LEG_REJECT)) {
                    return UserState.USER_BUSY;
                }
                if ((causeInteger != null && causeInteger == FinishState.RIGHT_LEG_RESPDND) || (statusCodeInteger != null && statusCodeInteger == FinishState.RIGHT_LEG_RESPDND)) {
                    return UserState.USER_NOT_RESPONSE;
                }
                return USER_UNACCESS;
            }
        }
        return USER_ALL_FORWARD;
    }
}
