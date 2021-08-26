package com.elarian.model;

public final class CustomerNumber {
    public String number;
    public Provider provider;

    public CustomerNumber(String number, Provider provider) {
        this.number = number;
        this.provider = provider;
    }

    public enum Provider {
        UNKNOWN(0),
        FACEBOOK(1),
        CELLULAR(2),
        TELEGRAM(3),
        APP(4),
        EMAIL(5);

        private final int value;

        Provider(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Provider valueOf(int value) {
            Provider[] values = Provider.values();
            for (Provider status: values) {
                if (status.value == value) {
                    return status;
                }
            }
            return UNKNOWN;
        }
    }
}
