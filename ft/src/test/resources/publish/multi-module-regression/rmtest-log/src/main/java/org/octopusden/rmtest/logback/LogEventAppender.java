package org.octopusden.rmtest.logback;

public interface LogEventAppender {

    void appendJsonLogEvent(String logEvent);
}
