package com.elarian.model;

import java.util.ArrayList;
import java.util.List;

public final class ReceivedMessage {
    public String messageId;
    public long createdAt;
    public String sessionId;
    public String inReplyTo;
    public CustomerNumber.Provider provider;
    public String appId;
    public Cash cost;
    // Parts
    public List<String> texts = new ArrayList<>();
    public List<Media> media = new ArrayList<>();
    public List<Location> locations = new ArrayList<>();
    public List<Email> emails = new ArrayList<>();
    public List<VoiceCallInput> voice = new ArrayList<>();
    public List<UssdInput> ussd = new ArrayList<>();
}
