package com.elarian.model;

public final class ConnectionConfig {
    public int port;
    public String host;
    public final long lifetime;
    public final long keepAlive;
    public final boolean isResumable;
    public final long reconnectTimeout;

    public ConnectionConfig(long lifetime, long keepAlive, boolean resumable, String host, int port, long reconnectTimeout) {
        this.lifetime = lifetime;
        this.keepAlive = keepAlive;
        this.isResumable = resumable;
        this.port = port;
        this.host = host;
        this.reconnectTimeout = reconnectTimeout;
    }

    public ConnectionConfig() {
        this(60000, 1000, true, "tcp.elarian.com", 443, 60000);
    }
}
