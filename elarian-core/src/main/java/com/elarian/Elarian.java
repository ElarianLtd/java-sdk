package com.elarian;

import com.elarian.model.*;
import com.elarian.hera.proto.AppModel;
import com.elarian.hera.proto.AppSocket;
import com.elarian.hera.proto.CommonModel;
import com.elarian.hera.proto.MessagingModel;
import com.elarian.model.WalletPaymentStatusNotification;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;

import java.util.List;
import java.util.function.Function;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public final class Elarian extends Client<AppSocket.ServerToAppNotification, AppSocket.ServerToAppNotificationReply> {

    private final Function<byte[], AppSocket.AppToServerCommandReply> replyDeserializer = (data) -> {
        try {
            return AppSocket.AppToServerCommandReply.newBuilder().mergeFrom(data).build();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    };

    private NotificationHandler<ReminderNotification> onReminderNotificationHandler;
    private NotificationHandler<MessagingSessionInitializedNotification> onMessagingSessionStartedNotificationHandler;
    private NotificationHandler<MessagingSessionInitializedNotification> onMessagingSessionRenewedNotificationHandler;
    private NotificationHandler<MessagingSessionEndedNotification> onMessagingSessionEndedNotificationHandler;
    private NotificationHandler<MessagingConsentUpdateNotification> onMessagingConsentUpdateNotificationHandler;
    private NotificationHandler<ReceivedSmsNotification> onReceivedSmsNotificationHandler;
    private NotificationHandler<ReceivedMediaNotification> onReceivedFbMessengerNotificationHandler;
    private NotificationHandler<ReceivedMediaNotification> onReceivedTelegramNotificationHandler;
    private NotificationHandler<ReceivedMediaNotification> onReceivedWhatsappNotificationHandler;
    private NotificationHandler<ReceivedMediaNotification> onReceivedEmailNotificationHandler;
    private BaseNotificationHandler<VoiceCallNotification, List<VoiceAction>> onVoiceCallBaseNotificationHandler;
    private BaseNotificationHandler<UssdSessionNotification, UssdMenu> onUssdSessionBaseNotificationHandler;
    private NotificationHandler<MessageStatusNotification> onMessageStatusNotificationHandler;
    private NotificationHandler<SentMessageReactionNotification> onSentMessageReactionNotificationHandler;
    private NotificationHandler<ReceivedPaymentNotification> onReceivedPaymentNotificationHandler;
    private NotificationHandler<PaymentStatusNotification> onPaymentStatusNotificationHandler;
    private NotificationHandler<WalletPaymentStatusNotification> onWalletPaymentStatusNotificationHandler;
    private NotificationHandler<CustomerActivityNotification> onCustomerActivityNotificationHandler;

    public Elarian(String apiKey, String orgId, String appId) {
        this(apiKey, orgId, appId, new ConnectionConfig(), true);
    }

    public Elarian(String apiKey, String orgId, String appId, ConnectionConfig connectionConfig, boolean allowNotifications) {
        super(new ClientConfig(apiKey, orgId, appId, connectionConfig, allowNotifications));

        registerGlobalNotificationHandler(notif -> new Mono<AppSocket.ServerToAppNotificationReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super AppSocket.ServerToAppNotificationReply> subscriber) {
                NotificationCallback<Message> responder = (incoming, appData) -> {
                    AppSocket.ServerToAppNotificationReply.Builder reply = AppSocket.ServerToAppNotificationReply.newBuilder();
                    if (appData != null) {
                        CommonModel.DataMapValue.Builder data = CommonModel.DataMapValue.newBuilder();
                        if (appData.bytes != null) {
                            data.setBytesVal(ByteString.copyFrom(appData.bytes));
                        } else if (appData.string != null) {
                            data.setStringVal(appData.string);
                        }
                        reply.setDataUpdate(AppSocket.AppDataUpdate.newBuilder()
                                .setData(data)
                                .build());
                    }

                    if (incoming != null) {
                        MessagingModel.OutboundMessage message = Utils.buildOutgoingMessage(new Message(incoming));
                        reply.setMessage(message);
                    }
                    subscriber.onNext(reply.build());
                    subscriber.onComplete();
                };

                if (notif.hasPurse()) {
                    handlePurseNotification(notif.getPurse(), responder);
                } else if (notif.hasCustomer()) {
                    handleCustomerNotification(notif.getCustomer(), responder);
                } else {
                    responder.callback(null, null);
                }
            }
        });
    }

    public void setOnVoiceCallNotificationHandler(BaseNotificationHandler<VoiceCallNotification, List<VoiceAction>> onVoiceCallBaseNotificationHandler) {
        this.onVoiceCallBaseNotificationHandler = onVoiceCallBaseNotificationHandler;
    }

    public void setOnWalletPaymentStatusNotificationHandler(NotificationHandler<WalletPaymentStatusNotification> onWalletPaymentStatusNotificationHandler) {
        this.onWalletPaymentStatusNotificationHandler = onWalletPaymentStatusNotificationHandler;
    }


    public void setOnUssdSessionNotificationHandler(BaseNotificationHandler<UssdSessionNotification, UssdMenu> onUssdSessionBaseNotificationHandler) {
        this.onUssdSessionBaseNotificationHandler = onUssdSessionBaseNotificationHandler;
    }

    public void setOnSentMessageReactionNotificationHandler(NotificationHandler<SentMessageReactionNotification> onSentMessageReactionNotificationHandler) {
        this.onSentMessageReactionNotificationHandler = onSentMessageReactionNotificationHandler;
    }

    public void setOnReminderNotificationHandler(NotificationHandler<ReminderNotification> onReminderNotificationHandler) {
        this.onReminderNotificationHandler = onReminderNotificationHandler;
    }

    public void setOnReceivedWhatsappNotificationHandler(NotificationHandler<ReceivedMediaNotification> onReceivedWhatsappNotificationHandler) {
        this.onReceivedWhatsappNotificationHandler = onReceivedWhatsappNotificationHandler;
    }

    public void setOnReceivedTelegramNotificationHandler(NotificationHandler<ReceivedMediaNotification> onReceivedTelegramNotificationHandler) {
        this.onReceivedTelegramNotificationHandler = onReceivedTelegramNotificationHandler;
    }

    public void setOnReceivedPaymentNotificationHandler(NotificationHandler<ReceivedPaymentNotification> onReceivedPaymentNotificationHandler) {
        this.onReceivedPaymentNotificationHandler = onReceivedPaymentNotificationHandler;
    }

    public void setOnReceivedSmsNotificationHandler(NotificationHandler<ReceivedSmsNotification> onReceivedSmsNotificationHandler) {
        this.onReceivedSmsNotificationHandler = onReceivedSmsNotificationHandler;
    }

    public void setOnReceivedEmailNotificationHandler(NotificationHandler<ReceivedMediaNotification> onReceivedEmailNotificationHandler) {
        this.onReceivedEmailNotificationHandler = onReceivedEmailNotificationHandler;
    }

    public void setOnPaymentStatusNotificationHandler(NotificationHandler<PaymentStatusNotification> onPaymentStatusNotificationHandler) {
        this.onPaymentStatusNotificationHandler = onPaymentStatusNotificationHandler;
    }

    public void setOnReceivedFbMessengerNotificationHandler(NotificationHandler<ReceivedMediaNotification> onReceivedFbMessengerNotificationHandler) {
        this.onReceivedFbMessengerNotificationHandler = onReceivedFbMessengerNotificationHandler;
    }

    public void setOnMessagingSessionStartedNotificationHandler(NotificationHandler<MessagingSessionInitializedNotification> onMessagingSessionStartedNotificationHandler) {
        this.onMessagingSessionStartedNotificationHandler = onMessagingSessionStartedNotificationHandler;
    }

    public void setOnMessagingSessionRenewedNotificationHandler(NotificationHandler<MessagingSessionInitializedNotification> onMessagingSessionRenewedNotificationHandler) {
        this.onMessagingSessionRenewedNotificationHandler = onMessagingSessionRenewedNotificationHandler;
    }

    public void setOnMessagingSessionEndedNotificationHandler(NotificationHandler<MessagingSessionEndedNotification> onMessagingSessionEndedNotificationHandler) {
        this.onMessagingSessionEndedNotificationHandler = onMessagingSessionEndedNotificationHandler;
    }

    public void setOnCustomerActivityNotificationHandler(NotificationHandler<CustomerActivityNotification> onCustomerActivityNotificationHandler) {
        this.onCustomerActivityNotificationHandler = onCustomerActivityNotificationHandler;
    }

    public void setOnMessageStatusNotificationHandler(NotificationHandler<MessageStatusNotification> onMessageStatusNotificationHandler) {
        this.onMessageStatusNotificationHandler = onMessageStatusNotificationHandler;
    }

    public void setOnMessagingConsentUpdateNotificationHandler(NotificationHandler<MessagingConsentUpdateNotification> onMessagingConsentUpdateNotificationHandler) {
        this.onMessagingConsentUpdateNotificationHandler = onMessagingConsentUpdateNotificationHandler;
    }


    /**
     * Generate auth token
     *
     * @return
     */
    public Mono<AuthToken> generateAuthToken() {
        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setGenerateAuthToken(AppSocket.GenerateAuthTokenCommand.newBuilder().build())
                .build();

        return new Mono<AuthToken>() {
            @Override
            public void subscribe(CoreSubscriber<? super AuthToken> subscriber) {
                buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.GenerateAuthTokenReply res = reply.getGenerateAuthToken();
                    AuthToken token = new AuthToken(res.getToken(), res.getLifetime().getSeconds());
                    subscriber.onNext(token);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     * Set a reminder to be triggered at the specified time for customers with the particular tag
     *
     * @param tag
     * @param reminder
     * @return
     */
    public Mono<TagUpdateReply> addCustomerReminderByTag(Tag tag, Reminder reminder) {

        AppModel.CustomerReminder.Builder rem = AppModel.CustomerReminder
                .newBuilder()
                .setKey(reminder.key)
                .setRemindAt(Timestamp.newBuilder()
                        .setSeconds(reminder.remindAt)
                        .build());

        if (reminder.payload != null) {
            rem.setPayload(StringValue.of(reminder.payload));
        }

        if (reminder.interval >= 60) {
            rem.setInterval(Duration.newBuilder()
                    .setSeconds(reminder.interval)
                    .build());
        }

        AppSocket.AddCustomerReminderTagCommand cmd = AppSocket.AddCustomerReminderTagCommand
                .newBuilder()
                .setTag(CommonModel.IndexMapping
                        .newBuilder()
                        .setKey(tag.key)
                        .setValue(StringValue.of(tag.value))
                        .build())
                .setReminder(rem)
                .build();
        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setAddCustomerReminderTag(cmd)
                .build();

        return new Mono<TagUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super TagUpdateReply> subscriber) {
                buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.TagCommandReply res = reply.getTagCommand();
                    TagUpdateReply tagUpdate = new TagUpdateReply(res.getStatus(), res.getWorkId().getValue(), res.getDescription());
                    subscriber.onNext(tagUpdate);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     * Cancels a previously set reminder with tag and key
     *
     * @param tag
     * @param key
     * @return
     */
    public Mono<TagUpdateReply> cancelCustomerReminderByTag(Tag tag, String key) {
        AppSocket.CancelCustomerReminderTagCommand cmd = AppSocket.CancelCustomerReminderTagCommand
                .newBuilder()
                .setTag(CommonModel.IndexMapping
                        .newBuilder()
                        .setKey(tag.key)
                        .setValue(StringValue.of(tag.value))
                        .build())
                .setKey(key)
                .build();
        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setCancelCustomerReminderTag(cmd)
                .build();

        return new Mono<TagUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super TagUpdateReply> subscriber) {
                buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.TagCommandReply res = reply.getTagCommand();
                    TagUpdateReply tagUpdate = new TagUpdateReply(res.getStatus(), res.getWorkId().getValue(), res.getDescription());
                    subscriber.onNext(tagUpdate);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     * Send a message by tag
     *
     * @param tag
     * @param channel
     * @param message
     * @return
     */
    public Mono<TagUpdateReply> sendMessageByTag(Tag tag, MessagingChannel channel, Message message) {
        AppSocket.SendMessageTagCommand cmd = AppSocket.SendMessageTagCommand
                .newBuilder()
                .setTag(CommonModel.IndexMapping
                        .newBuilder()
                        .setKey(tag.key)
                        .setValue(StringValue.of(tag.value))
                        .build())
                .setMessage(Utils.buildOutgoingMessage(message))
                .setChannelNumber(MessagingModel.MessagingChannelNumber
                        .newBuilder()
                        .setNumber(channel.number)
                        .setChannelValue(channel.channel.getValue())
                        .build())
                .build();
        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setSendMessageTag(cmd)
                .build();
        return new Mono<TagUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super TagUpdateReply> subscriber) {
                buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.TagCommandReply res = reply.getTagCommand();
                    TagUpdateReply tagUpdate = new TagUpdateReply(res.getStatus(), res.getWorkId().getValue(), res.getDescription());
                    subscriber.onNext(tagUpdate);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    // From customer
    public Mono<InitiatePaymentReply> initiatePayment(PaymentCustomerCounterParty debitParty, PaymentCustomerCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentCustomerCounterParty debitParty, PaymentPurseCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentCustomerCounterParty debitParty, PaymentWalletCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentCustomerCounterParty debitParty, PaymentChannelCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    // From purse
    public Mono<InitiatePaymentReply> initiatePayment(PaymentPurseCounterParty debitParty, PaymentCustomerCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentPurseCounterParty debitParty, PaymentPurseCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentPurseCounterParty debitParty, PaymentWalletCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentPurseCounterParty debitParty, PaymentChannelCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    // From Wallet
    public Mono<InitiatePaymentReply> initiatePayment(PaymentWalletCounterParty debitParty, PaymentCustomerCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentWalletCounterParty debitParty, PaymentPurseCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentWalletCounterParty debitParty, PaymentWalletCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentWalletCounterParty debitParty, PaymentChannelCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    // From Channel
    public Mono<InitiatePaymentReply> initiatePayment(PaymentChannelCounterParty debitParty, PaymentCustomerCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentChannelCounterParty debitParty, PaymentPurseCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentChannelCounterParty debitParty, PaymentWalletCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    public Mono<InitiatePaymentReply> initiatePayment(PaymentChannelCounterParty debitParty, PaymentChannelCounterParty creditParty, Cash value) {
        return initiatePayment(new PaymentCounterParty(debitParty), new PaymentCounterParty(creditParty), value);
    }

    /**
     * Initiate a payment transaction
     *
     * @param debitParty
     * @param creditParty
     * @param value
     * @return
     */
    private Mono<InitiatePaymentReply> initiatePayment(PaymentCounterParty debitParty, PaymentCounterParty creditParty, Cash value) {
        AppSocket.InitiatePaymentCommand cmd = AppSocket.InitiatePaymentCommand
                .newBuilder()
                .setDebitParty(Utils.buildPaymentCounterParty(debitParty))
                .setCreditParty(Utils.buildPaymentCounterParty(creditParty))
                .setValue(CommonModel.Cash
                        .newBuilder()
                        .setAmount(value.amount)
                        .setCurrencyCode(value.currencyCode)
                        .build())
                .build();
        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setInitiatePayment(cmd)
                .build();

        return new Mono<InitiatePaymentReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super InitiatePaymentReply> subscriber) {
                buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.InitiatePaymentReply res = reply.getInitiatePayment();
                    InitiatePaymentReply result = new InitiatePaymentReply();
                    result.status = PaymentStatus.valueOf(res.getStatusValue());
                    result.transactionId = res.getTransactionId().getValue();
                    result.description = res.getDescription();
                    result.debitCustomerId = res.getDebitCustomerId().getValue();
                    result.creditCustomerId = res.getCreditCustomerId().getValue();
                    subscriber.onNext(result);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    private void handlePurseNotification(AppSocket.ServerToAppPurseNotification notif, NotificationCallback<Message> responder) {
        if (onPaymentStatusNotificationHandler != null) {
            PaymentStatusNotification payload = new PaymentStatusNotification();
            payload.orgId = notif.getOrgId();
            payload.appId = notif.getAppId();
            payload.purseId = notif.getPurseId();
            payload.createdAt = notif.getCreatedAt().getSeconds();
            payload.transactionId = notif.getPaymentStatus().getTransactionId();
            payload.status = PaymentStatus.valueOf(notif.getPaymentStatus().getStatusValue());
            onPaymentStatusNotificationHandler.handle(payload, null, null, responder);
        } else {
            responder.callback(null, null);
        }
    }

    private void handleCustomerNotification(AppSocket.ServerToAppCustomerNotification notif, NotificationCallback<Message> responder) {
        DataValue appData = null;
        try {
            if (notif.hasAppData()) {
                String strVal = notif.getAppData().getStringVal();
                ByteString byteString = notif.getAppData().getBytesVal();
                if (byteString != null && !byteString.isEmpty()) {
                    appData = DataValue.of(byteString.toByteArray());
                } else if (strVal != null && !strVal.isEmpty()) {
                    appData = DataValue.of(strVal);
                }
            }

            Customer customer = new Customer(this, notif.getCustomerId());

            if (notif.hasReminder() && onReminderNotificationHandler != null) {

                ReminderNotification payload = Utils.fillInCustomerNotification(notif, new ReminderNotification());
                payload.workId = notif.getReminder().getWorkId().getValue();
                AppModel.CustomerReminder reminder = notif.getReminder().getReminder();
                payload.reminder = new Reminder(reminder.getKey(), reminder.getPayload().getValue(), reminder.getRemindAt().getSeconds());
                payload.reminder.interval = reminder.getInterval().getSeconds();
                CommonModel.CustomerIndex tag = notif.getReminder().getTag();
                payload.tag = new Tag(tag.getMapping().getKey(), tag.getMapping().getValue().getValue(), tag.getExpiresAt().getSeconds());

                responder.callback(null, appData);
                onReminderNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasMessagingSessionStarted() && onMessagingSessionStartedNotificationHandler != null) {

                MessagingSessionInitializedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionInitializedNotification());
                AppSocket.MessagingSessionStartedNotification msg = notif.getMessagingSessionStarted();
                payload.sessionId = msg.getSessionId();
                payload.expiresAt = msg.getExpiresAt().getSeconds();
                payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onMessagingSessionStartedNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasMessagingSessionRenewed() && onMessagingSessionRenewedNotificationHandler != null) {

                MessagingSessionInitializedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionInitializedNotification());
                AppSocket.MessagingSessionRenewedNotification msg = notif.getMessagingSessionRenewed();
                payload.sessionId = msg.getSessionId();
                payload.expiresAt = msg.getExpiresAt().getSeconds();
                payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onMessagingSessionRenewedNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasMessagingSessionEnded() && onMessagingSessionEndedNotificationHandler != null) {

                MessagingSessionEndedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionEndedNotification());
                AppSocket.MessagingSessionEndedNotification msg = notif.getMessagingSessionEnded();
                payload.sessionId = msg.getSessionId();
                payload.duration = msg.getDuration().getSeconds();
                payload.reason = MessagingSessionEndReason.valueOf(msg.getReasonValue());
                payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onMessagingSessionEndedNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasMessagingConsentUpdate() && onMessagingConsentUpdateNotificationHandler != null) {

                MessagingConsentUpdateNotification payload = Utils.fillInCustomerNotification(notif, new MessagingConsentUpdateNotification());
                AppSocket.MessagingConsentUpdateNotification msg = notif.getMessagingConsentUpdate();
                payload.update = ConsentAction.valueOf(msg.getUpdateValue());
                payload.status = MessagingConsentUpdateStatus.valueOf(msg.getStatusValue());
                payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onMessagingConsentUpdateNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasReceivedMessage()) {
                AppSocket.ReceivedMessageNotification msg = notif.getReceivedMessage();
                MessagingChannel.Channel channel = MessagingChannel.Channel.valueOf(msg.getChannelNumber().getChannel().getNumber());

                switch (channel.getValue()) {
                    case 1: // MessagingChannel.Channel.SMS
                        ReceivedSmsNotification sms = Utils.fillInCustomerNotification(notif, new ReceivedSmsNotification());
                        sms.messageId = msg.getMessageId();
                        sms.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                        sms.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                        customer.customerNumber = sms.customerNumber;

                        sms.text = null;
                        if (msg.getPartsCount() > 0) {
                            sms.text = msg.getParts(0).getText();
                        }

                        if (onReceivedSmsNotificationHandler != null) {
                            responder.callback(null, appData);
                            onReceivedSmsNotificationHandler.handle(sms, customer, appData);
                            return;
                        }
                        break;
                    case 2: // MessagingChannel.Channel.VOICE
                        if (onVoiceCallBaseNotificationHandler != null) {
                            VoiceCallNotification voiceNotif = Utils.fillInCustomerNotification(notif, new VoiceCallNotification());
                            voiceNotif.messageId = msg.getMessageId();
                            voiceNotif.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                            voiceNotif.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());
                            voiceNotif.sessionId = msg.getSessionId().getValue();

                            customer.customerNumber = voiceNotif.customerNumber;

                            if (msg.getPartsCount() > 0) {
                                voiceNotif.voice = Utils.makeVoiceCallInput(msg.getParts(0).getVoice());
                            }

                            onVoiceCallBaseNotificationHandler.handle(voiceNotif, customer, appData, (actions, data) -> {
                                MessageBody body = new MessageBody();
                                body.voice = actions;
                                responder.callback(new Message(body), data);
                            });
                            return;
                        }
                        break;
                    case 3: // MessagingChannel.Channel.USSD:
                        if (onUssdSessionBaseNotificationHandler != null) {
                            UssdSessionNotification ussd = Utils.fillInCustomerNotification(notif, new UssdSessionNotification());
                            ussd.messageId = msg.getMessageId();
                            ussd.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
                            ussd.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

                            customer.customerNumber = ussd.customerNumber;

                            ussd.sessionId = msg.getSessionId().getValue();
                            ussd.input = null;
                            if (msg.getPartsCount() > 0) {
                                MessagingModel.UssdInputMessageBody rawInput = msg.getParts(0).getUssd();
                                ussd.input = new UssdInput(
                                        rawInput.getText().getValue(),
                                        UssdInput.UssdStatus.valueOf(rawInput.getStatusValue())
                                );
                            }
                            onUssdSessionBaseNotificationHandler.handle(ussd, customer, appData, (message, data) -> {
                                MessageBody body = new MessageBody();
                                body.ussd = message;
                                Message resp = new Message(body);
                                responder.callback(resp, data);
                            });
                            return;
                        }
                        break;
                    case 4: // MessagingChannel.Channel.FB_MESSENGER
                        if (onReceivedFbMessengerNotificationHandler != null) {
                            ReceivedMediaNotification fb_messenger = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                            Utils.fillInMediaMessageNotification(msg, fb_messenger, customer);
                            responder.callback(null, appData);
                            onReceivedFbMessengerNotificationHandler.handle(fb_messenger, customer, appData);
                            return;
                        }
                        break;
                    case 5: // MessagingChannel.Channel.TELEGRAM
                        if (onReceivedTelegramNotificationHandler != null) {
                            ReceivedMediaNotification telegram = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                            Utils.fillInMediaMessageNotification(msg, telegram, customer);
                            responder.callback(null, appData);
                            onReceivedTelegramNotificationHandler.handle(telegram, customer, appData);
                            return;
                        }
                        break;
                    case 6: // MessagingChannel.Channel.WHATSAPP
                        if (onReceivedWhatsappNotificationHandler != null) {
                            ReceivedMediaNotification whatsapp = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                            Utils.fillInMediaMessageNotification(msg, whatsapp, customer);
                            responder.callback(null, appData);
                            onReceivedWhatsappNotificationHandler.handle(whatsapp, customer, appData);
                            return;
                        }
                        break;
                    case 7: // MessagingChannel.Channel.EMAIL
                        if (onReceivedEmailNotificationHandler != null) {
                            ReceivedMediaNotification email = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                            Utils.fillInMediaMessageNotification(msg, email, customer);
                            responder.callback(null, appData);
                            onReceivedEmailNotificationHandler.handle(email, customer, appData);
                            return;
                        }
                        break;
                }

                responder.callback(null, appData);

            } else if (notif.hasMessageStatus() && onMessageStatusNotificationHandler != null) {

                MessageStatusNotification payload = Utils.fillInCustomerNotification(notif, new MessageStatusNotification());
                payload.messageId = notif.getMessageStatus().getMessageId();
                payload.status = MessageDeliveryStatus.valueOf(notif.getMessageStatus().getStatusValue());

                responder.callback(null, appData);
                onMessageStatusNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasSentMessageReaction() && onSentMessageReactionNotificationHandler != null) {

                SentMessageReactionNotification payload = Utils.fillInCustomerNotification(notif, new SentMessageReactionNotification());

                payload.messageId = notif.getSentMessageReaction().getMessageId();
                payload.channelNumber = Utils.makeMessagingChannel(notif.getSentMessageReaction().getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(notif.getSentMessageReaction().getCustomerNumber());
                payload.reaction = MessageReaction.valueOf(notif.getSentMessageReaction().getReactionValue());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onSentMessageReactionNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasReceivedPayment() && onReceivedPaymentNotificationHandler != null) {

                ReceivedPaymentNotification payload = Utils.fillInCustomerNotification(notif, new ReceivedPaymentNotification());

                payload.purseId = notif.getReceivedPayment().getPurseId();
                payload.transactionId = notif.getReceivedPayment().getTransactionId();
                payload.channelNumber = Utils.makePaymentChannel(notif.getReceivedPayment().getChannelNumber());
                payload.customerNumber = Utils.makeCustomerNumber(notif.getReceivedPayment().getCustomerNumber());
                payload.status = PaymentStatus.valueOf(notif.getReceivedPayment().getStatus().getNumber());
                payload.value = new Cash(notif.getReceivedPayment().getValue().getCurrencyCode(), notif.getReceivedPayment().getValue().getAmount());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onReceivedPaymentNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasPaymentStatus() && onPaymentStatusNotificationHandler != null) {

                PaymentStatusNotification payload = Utils.fillInCustomerNotification(notif, new PaymentStatusNotification());
                payload.transactionId = notif.getPaymentStatus().getTransactionId();
                payload.status = PaymentStatus.valueOf(notif.getPaymentStatus().getStatusValue());

                responder.callback(null, appData);
                onPaymentStatusNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasWalletPaymentStatus() && onWalletPaymentStatusNotificationHandler != null) {

                WalletPaymentStatusNotification payload = Utils.fillInCustomerNotification(notif, new WalletPaymentStatusNotification());
                payload.walletId = notif.getWalletPaymentStatus().getWalletId();
                payload.transactionId = notif.getWalletPaymentStatus().getTransactionId();
                payload.status = PaymentStatus.valueOf(notif.getWalletPaymentStatus().getStatusValue());

                responder.callback(null, appData);
                onWalletPaymentStatusNotificationHandler.handle(payload, customer, appData);

            } else if (notif.hasCustomerActivity() && onCustomerActivityNotificationHandler != null) {

                CustomerActivityNotification payload = Utils.fillInCustomerNotification(notif, new CustomerActivityNotification());
                payload.sessionId = notif.getCustomerActivity().getSessionId();
                payload.activity = new Activity(
                        notif.getCustomerActivity().getActivity().getKey(),
                        notif.getCustomerActivity().getActivity().getPropertiesMap(),
                        notif.getCustomerActivity().getSessionId(),
                        notif.getCustomerActivity().getActivity().getCreatedAt().getSeconds()
                );
                payload.source = notif.getCustomerActivity().getSource();
                payload.customerNumber = Utils.makeCustomerNumber(notif.getCustomerActivity().getCustomerNumber());

                customer.customerNumber = payload.customerNumber;

                responder.callback(null, appData);
                onCustomerActivityNotificationHandler.handle(payload, customer, appData);
            } else {
                responder.callback(null, appData);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            responder.callback(null, appData);
        }
    }

    @Override
    protected byte[] serializeNotificationReply(AppSocket.ServerToAppNotificationReply data) {
        return data.toByteArray();
    }

    @Override
    protected byte[] serializeSetupPayload(ClientConfig opts) {
        AppSocket.AppConnectionMetadata.Builder builder = AppSocket.AppConnectionMetadata.newBuilder()
                .setAppId(opts.appId)
                .setOrgId(opts.orgId)
                .setApiKey(StringValue.newBuilder().setValue(opts.apiKey));

        builder.setSimplexMode(!opts.allowNotifications);
        builder.setSimulatorMode(false);
        return builder.build().toByteArray();
    }

    @Override
    protected AppSocket.ServerToAppNotification deserializeNotification(byte[] data) throws RuntimeException {
        try {
            return AppSocket.ServerToAppNotification.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
