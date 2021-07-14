package com.elarian.model;

public final class ConnectionConfig {
    public int port;
    public String host;
    public final long lifetime;
    public final long keepAlive;
    public final boolean isResumable;

    public ConnectionConfig(long lifetime, long keepAlive, boolean resumable, String host, int port) {
        this.lifetime = lifetime;
        this.keepAlive = keepAlive;
        this.isResumable = resumable;
        this.port = port;
        this.host = host;
    }

    public ConnectionConfig() {
        this(60000, 1000, true, "tcp.elarian.com", 8082);
    }
}
