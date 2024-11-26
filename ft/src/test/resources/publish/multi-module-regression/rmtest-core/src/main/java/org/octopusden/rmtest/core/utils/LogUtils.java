package org.octopusden.rmtest.core.utils;


public class LogUtils {

    public static void setLogContext(String name, String value) {
    }

    public static void setTaskId(String taskId) {
        setLogContext("id", taskId);
    }

    public static void setSagaId(String sagaId) {
        setLogContext("saga", sagaId);
    }

    public static void setFrom(String from) {
        setLogContext("from", from);
    }

}
