package com.elarian.model;

public enum PaymentMode {
    UNKNOWN(0),
    HOSTED(1),
    VIRTUAL(2);

    private final int value;

    PaymentMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static PaymentMode valueOf(int value) {
        PaymentMode[] values = PaymentMode.values();
        for (PaymentMode mode: values) {
            if (mode.value == value) {
                return mode;
            }
        }
        return UNKNOWN;
    }
}