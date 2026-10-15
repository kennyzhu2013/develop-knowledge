package com.cmic.util;

public class ModelUtil {

    private static final String SEPARATOR = "_";

    public static String buildModelJobId(String jobId, String dataId) {
        return jobId + SEPARATOR + dataId;
    }

    public static String buildModelJobId(Long jobId, String dataId) {
        return jobId + SEPARATOR + dataId;
    }

    public static String getOriginJobId(String modelJobId) {
        int idx = modelJobId.indexOf(SEPARATOR);
        if (idx == -1) {
            return modelJobId;
        }
        return modelJobId.substring(0, idx);
    }

    public static String getDataId(String modelJobId) {
        int idx = modelJobId.indexOf(SEPARATOR);
        if (idx == -1) {
            return null;
        }
        return modelJobId.substring(idx + 1);
    }
}
