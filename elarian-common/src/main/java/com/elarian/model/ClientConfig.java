package com.elarian.model;

import reactor.util.annotation.NonNull;

public final class ClientConfig {
    public String authToken;
    public final String appId;
    public final String orgId;
    public final String apiKey;
    public boolean allowNotifications;
    public ConnectionConfig connectionConfig;

    public ClientConfig(@NonNull String apiKey, @NonNull String orgId, @NonNull String appId) {
        this.orgId = orgId;
        this.appId = appId;
        this.apiKey = apiKey;
        this.authToken = null;
        this.allowNotifications = true;
        this.connectionConfig = new ConnectionConfig();
    }

    public ClientConfig(@NonNull String apiKey, @NonNull String orgId, @NonNull String appId, ConnectionConfig connectionConfig, boolean allowNotifications) {
        this.orgId = orgId;
        this.appId = appId;
        this.apiKey = apiKey;
        this.authToken = null;
        this.allowNotifications = allowNotifications;
        this.connectionConfig = connectionConfig;
    }

    public ClientConfig(@NonNull String authToken, @NonNull String orgId, @NonNull String appId, ConnectionConfig connectionConfig) {
        this.orgId = orgId;
        this.appId = appId;
        this.authToken = authToken;
        this.apiKey = null;
        this.allowNotifications = false;
        this.connectionConfig = connectionConfig;
    }
}
