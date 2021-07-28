package com.elarian.model;

import java.util.ArrayList;
import java.util.List;

public final class MessagingState {
    public List<MessagingChannelState> channels = new ArrayList<>();
    public List<ChannelMessage> messages = new ArrayList<>();
    public List<CompleteMessagingSession> sessions = new ArrayList<>();
}

