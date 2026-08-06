package com.example.c4editor.domain;

import java.util.Arrays;

public enum AuthScope {
    AGENT_READ("agent:read"),
    MODEL_PROPOSE("model:propose"),
    MODEL_WRITE("model:write");

    private final String value;

    AuthScope(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AuthScope fromValue(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown auth scope: " + value));
    }
}
