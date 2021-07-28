package com.elarian.model;

import java.util.ArrayList;
import java.util.List;

public final class CompleteMessagingSession {
    public CustomerNumber customerNumber;
    public MessagingChannel channelNumber;
    public String sessionId;
    public long duration;
    public long startedAt;
    public Cash cost;
    public List<String> appIds = new ArrayList<>();
    public MessagingSessionEndReason endReason;
}
