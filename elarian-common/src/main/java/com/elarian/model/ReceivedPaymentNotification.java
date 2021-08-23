package com.elarian.model;

public final class ReceivedPaymentNotification extends Notification {
    public String purseId;
    public String transactionId;
    public CustomerNumber customerNumber;
    public PaymentChannel channelNumber;
    public Cash value;
    public PaymentStatus status;
}
