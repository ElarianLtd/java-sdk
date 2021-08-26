package com.elarian.model;

import java.util.ArrayList;
import java.util.List;

public final class ActivityState {
    public List<Session> sessions = new ArrayList<>();
    public List<CustomerNumber> customerNumbers = new ArrayList<>();

    public static final class Session {
        public CustomerNumber customerNumber;
        public String source;
        public String sessionId;
        public String appId;
        public List<Activity> activities;
        public long createdAt;
        public long updatedAt;
    }
}
