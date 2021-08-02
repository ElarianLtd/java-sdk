package com.elarian.model;

public class PaymentChannelCounterParty {
    public PaymentChannel.Channel channel;
    public int networkCode;
    public String account;
    public String source;
    public String destination;

    public PaymentChannelCounterParty(PaymentChannel.Channel channel, String account, String source, String destination, int networkCode) {
        this.channel = channel;
        this.source = source;
        this.destination = destination;
        this.account = account;
        this.networkCode = networkCode;
    }
}
