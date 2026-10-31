package com.devrats.services.activity;

public record EligibilityDecision(boolean eligible, String reason) {

    public static EligibilityDecision allow() {
        return new EligibilityDecision(true, null);
    }

    public static EligibilityDecision deny(String reason) {
        return new EligibilityDecision(false, reason);
    }
}
