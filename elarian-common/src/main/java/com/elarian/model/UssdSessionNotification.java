package com.elarian.model;

public final class UssdSessionNotification extends Notification {
    public String messageId;
    public CustomerNumber customerNumber;
    public MessagingChannel channelNumber;
    public UssdInput input;
    public String sessionId;
}
