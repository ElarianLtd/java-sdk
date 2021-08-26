package com.elarian.test;

import com.elarian.ConnectionListener;
import com.elarian.model.MessagingChannel;
import com.elarian.model.PaymentChannel;

public class Fixtures {

    public static final String APP_ID = System.getenv("APP_ID");
    public static final String ORG_ID = System.getenv("ORG_ID");
    public static final String API_KEY = System.getenv("API_KEY");
    public static final String SMS_SENDER_ID = System.getenv("SMS_SENDER_ID");
    public static final String SMS_SHORT_CODE = System.getenv("SMS_SHORT_CODE");
    public static final String USSD_CODE = System.getenv("USSD_CODE");
    public static final String VOICE_NUMBER = System.getenv("VOICE_NUMBER");
    public static final String TELEGRAM_NUMBER = System.getenv("TELEGRAM_NUMBER");
    public static final String MESSENNGER_NUMBER = System.getenv("MESSENNGER_NUMBER");
    public static final String MPESA_PAYBILL = System.getenv("MPESA_PAYBILL");
    public static final String WHATSAPP_NUMBER = System.getenv("WHATSAPP_NUMBER");
    public static final String PURSE_ID = System.getenv("PURSE_ID");

    public static final MessagingChannel smsChannel = new MessagingChannel(SMS_SHORT_CODE, MessagingChannel.Channel.SMS);
    public static final MessagingChannel telegramChannel = new MessagingChannel(TELEGRAM_NUMBER, MessagingChannel.Channel.TELEGRAM);
    public static final MessagingChannel whatsappChannel = new MessagingChannel(WHATSAPP_NUMBER, MessagingChannel.Channel.WHATSAPP);
    public static final MessagingChannel messenngerChannel = new MessagingChannel(MESSENNGER_NUMBER, MessagingChannel.Channel.FB_MESSENGER);
    public static final MessagingChannel voiceChannel = new MessagingChannel(VOICE_NUMBER, MessagingChannel.Channel.VOICE);
    public static final PaymentChannel mpesaChannel = new PaymentChannel(MPESA_PAYBILL, PaymentChannel.Channel.CELLULAR);
    public static final String activitySource = "fake-web-app.com";

    public static final ConnectionListener connectionListener = new ConnectionListener() {
        @Override
        public void onPending() {

        }

        @Override
        public void onConnecting() {

        }

        @Override
        public void onClosed() {

        }

        @Override
        public void onConnected() {

        }

        @Override
        public void onError(Throwable throwable) {
            throw new Error(throwable);
        }
    };
}
