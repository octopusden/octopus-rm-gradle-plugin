package org.octopusden.rmtest.logback;

public interface ICloudLogEvent {

    String getTaskId();
    String getSagaId();
    String getSource();
    long   getTimeout();
    String getApi();

    default String getArticle() { return null; }
}
