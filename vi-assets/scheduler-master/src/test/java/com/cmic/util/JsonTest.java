package com.cmic.util;

import com.cmic.pojo.entity.Process;

public class JsonTest {

    public static void main(String[] args) {
        Process process = Process.builder()
                .processId("123")
                .processName("test")
                .xmlFileName("test.xml")
                .build();

//        String json = "{\"requestId\":\"10\",\"processId\":\"px41ixj\",\"processName\":\"asr-华为\",\"xmlFileName\":\"asr_huawei\",\"timeout\":2,\"aiModels\":\"asr,huaweiQC\",\"status\":1}";
        String json = JacksonUtil.obj2String(process);
        Process updatedProcess = JacksonUtil.string2Obj(json, Process.class);
        System.out.println(updatedProcess);
    }

}
