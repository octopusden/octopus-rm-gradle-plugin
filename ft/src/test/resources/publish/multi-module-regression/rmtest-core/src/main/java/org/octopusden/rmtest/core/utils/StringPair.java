package org.octopusden.rmtest.core.utils;

import java.util.Map;

public class StringPair implements Map.Entry<String, String> {
    private final String key;
    private String value;

    public StringPair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String setValue(String value) {
        final String oldValue = this.value;
        this.value = value;
        return oldValue;
    }
}
