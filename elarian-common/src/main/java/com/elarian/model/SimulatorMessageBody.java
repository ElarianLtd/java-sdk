package com.elarian.model;

public final class SimulatorMessageBody {
    public String text = null;
    public Media media = null;
    public Location location = null;
    public Email email = null;
    public VoiceCallInput voice = null;
    public UssdInput ussd = null;

    enum StringType {
        TEXT,
        USSD
    }

    public SimulatorMessageBody() {}

    public SimulatorMessageBody(String entry, SimulatorMessageBody.StringType type) {
        switch (type) {
            case USSD:
                this.ussd = new UssdInput(entry, UssdInput.UssdStatus.ACTIVE);
                break;
            case TEXT:
                this.text = entry;
                break;
        }
    }

    public SimulatorMessageBody(String text) {
        this(text, StringType.TEXT);
    }

    public SimulatorMessageBody(Media media) {
        this.media = media;
    }

    public SimulatorMessageBody(Location location) {
        this.location = location;
    }

    public SimulatorMessageBody(Email email) {
        this.email = email;
    }

    public SimulatorMessageBody(VoiceCallInput voice) {
        this.voice = voice;
    }
}

