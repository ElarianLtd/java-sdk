package com.elarian.model;

public final class PaymentChannel {
    public String number;
    public Channel channel;

    public PaymentChannel(String number, Channel channel) {
        this.number = number;
        this.channel = channel;
    }

    public enum Channel {
        UNKNOWN(0),
        CELLULAR(1),
        AIRTIME(2);

        private final int value;

        Channel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Channel valueOf(int value) {
            Channel[] values = Channel.values();
            for (Channel status: values) {
                if (status.value == value) {
                    return status;
                }
            }
            return UNKNOWN;
        }
    }
}
