package com.elarian.model;

public enum PaymentStatus {
    UNKNOWN(0),
    QUEUED(100),
    PENDING_CONFIRMATION(101),
    PENDING_VALIDATION(102),
    VALIDATED(103),
    INVALID_REQUEST(200),
    NOT_SUPPORTED(201),
    INSUFFICIENT_FUNDS(202),
    APPLICATION_ERROR(203),
    NOT_ALLOWED(204),
    DUPLICATE_REQUEST(205),
    INVALID_PURSE(206),
    INVALID_WALLET(207),
    DECOMMISSIONED_CUSTOMER_ID(299),
    SUCCESS(300),
    FAILED(400),
    THROTTLED(401),
    EXPIRED(402),
    REJECTED(403),
    REVERSED(500);

    private final int value;

    PaymentStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static PaymentStatus valueOf(int value) {
        PaymentStatus[] values = PaymentStatus.values();
        for (PaymentStatus status: values) {
            if (status.value == value) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
