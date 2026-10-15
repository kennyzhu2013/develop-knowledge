package com.cmic.service;

import org.slf4j.MDC;

public class Slf4jMDCService {
    public static void initMDCCustomerCallID(){
        MDC.put("CustomerCallId", "");
    }

    public static void putMDCCustomerCallID(long customerCallId){
        MDC.put("CustomerCallId", customerCallId+"");
    }

    public static void putMDCCustomerCallID(String customerCallId){
        MDC.put("CustomerCallId", customerCallId);
    }
}
