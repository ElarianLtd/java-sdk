package com.elarian.model;

public final class UssdInput {
    public UssdStatus status;
    public String text;

    public UssdInput() {}

    public UssdInput(String text, UssdStatus status) {
        this.text = text;
        this.status = status;
    }

    public enum UssdStatus {
        UNKNOWN(0),
        ACTIVE(100),
        INCOMPLETE(200),
        COMPLETED(201),
        APP_ERROR(300);

        private final int value;

        UssdStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static UssdStatus valueOf(int value) {
            UssdStatus[] values = UssdStatus.values();
            for (UssdStatus status : values) {
                if (status.value == value) {
                    return status;
                }
            }
            return UNKNOWN;
        }
    }
}
