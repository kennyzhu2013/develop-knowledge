package com.cmic.util;

public class ExceptionUtil {

    public static String getStackTraceString(Throwable ex) {
        StringBuilder traceBuilder = new StringBuilder();
        assert ex.getStackTrace() != null;
        for (StackTraceElement traceElement : ex.getStackTrace()) {
            traceBuilder.append(traceElement.toString());
            traceBuilder.append("\n");
        }
        return traceBuilder.toString();
    }

    public static String buildErrorMessage(Exception ex) {
        return String.format("%s : %s \r\n %s", ex.toString(), ex.getMessage(), getStackTraceString(ex));
    }
}
