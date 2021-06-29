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
    private BaseNotificationHandler<ReminderNotification, MessageBody> onReminderBaseNotificationHandler;
    private BaseNotificationHandler<MessagingSessionInitializedNotification, MessageBody> onMessagingSessionStartedBaseNotificationHandler;
    private BaseNotificationHandler<MessagingSessionInitializedNotification, MessageBody> onMessagingSessionRenewedBaseNotificationHandler;
    private BaseNotificationHandler<MessagingSessionEndedNotification, MessageBody> onMessagingSessionEndedBaseNotificationHandler;
    private BaseNotificationHandler<MessagingConsentUpdateNotification, MessageBody> onMessagingConsentUpdateBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedSmsNotification, MessageBody> onReceivedSmsBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedFbMessengerBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedTelegramBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedWhatsappBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedEmailBaseNotificationHandler;
    private BaseNotificationHandler<VoiceCallNotification, List<VoiceAction>> onVoiceCallBaseNotificationHandler;
    private BaseNotificationHandler<UssdSessionNotification, UssdMenu> onUssdSessionBaseNotificationHandler;
    private BaseNotificationHandler<MessageStatusNotification, MessageBody> onMessageStatusBaseNotificationHandler;
    private BaseNotificationHandler<SentMessageReactionNotification, MessageBody> onSentMessageReactionBaseNotificationHandler;
    private BaseNotificationHandler<ReceivedPaymentNotification, MessageBody> onReceivedPaymentBaseNotificationHandler;
    private BaseNotificationHandler<PaymentStatusNotification, MessageBody> onPaymentStatusBaseNotificationHandler;
    private BaseNotificationHandler<WalletPaymentStatusNotification, MessageBody> onWalletPaymentStatusBaseNotificationHandler;
    private BaseNotificationHandler<CustomerActivityNotification, MessageBody> onCustomerActivityBaseNotificationHandler;

    public Elarian(String authToken, String orgId, String appId) {
        this(authToken, orgId, appId, new ConnectionConfig());
    }

    public Elarian(String authToken, String orgId, String appId, ConnectionConfig connectionConfig) {
        super(new ClientConfig(authToken, orgId, appId, connectionConfig));

        registerGlobalNotificationHandler(notif -> new Mono<AppSocket.ServerToAppNotificationReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super AppSocket.ServerToAppNotificationReply> subscriber) {

                NotificationCallback<MessageBody> responder = (incoming, appData) -> {
                    MessagingModel.OutboundMessage message = Utils.buildOutgoingMessage(new Message(incoming));
                    CommonModel.DataMapValue.Builder data = CommonModel.DataMapValue.newBuilder();
                    if (appData != null) {
                        if (appData.bytes != null) {
                            data.setBytesVal(ByteString.copyFrom(appData.bytes));
                        } else if (appData.string != null) {
                            data.setStringVal(appData.string);
                        }
                    }
                    AppSocket.ServerToAppNotificationReply reply = AppSocket.ServerToAppNotificationReply.newBuilder()
                            .setMessage(message)
                            .setDataUpdate(AppSocket.AppDataUpdate.newBuilder()
                                    .setData(data)
                                    .build())
                            .build();
                    subscriber.onNext(reply);
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

    public void setOnWalletPaymentStatusNotificationHandler(BaseNotificationHandler<WalletPaymentStatusNotification, MessageBody> onWalletPaymentStatusBaseNotificationHandler) {
        this.onWalletPaymentStatusBaseNotificationHandler = onWalletPaymentStatusBaseNotificationHandler;
    }


    public void setOnUssdSessionNotificationHandler(BaseNotificationHandler<UssdSessionNotification, UssdMenu> onUssdSessionBaseNotificationHandler) {
        this.onUssdSessionBaseNotificationHandler = onUssdSessionBaseNotificationHandler;
    }

    public void setOnSentMessageReactionNotificationHandler(BaseNotificationHandler<SentMessageReactionNotification, MessageBody> onSentMessageReactionBaseNotificationHandler) {
        this.onSentMessageReactionBaseNotificationHandler = onSentMessageReactionBaseNotificationHandler;
    }

    public void setOnReminderNotificationHandler(BaseNotificationHandler<ReminderNotification, MessageBody> onReminderBaseNotificationHandler) {
        this.onReminderBaseNotificationHandler = onReminderBaseNotificationHandler;
    }

    public void setOnReceivedWhatsappNotificationHandler(BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedWhatsappBaseNotificationHandler) {
        this.onReceivedWhatsappBaseNotificationHandler = onReceivedWhatsappBaseNotificationHandler;
    }

    public void setOnReceivedTelegramNotificationHandler(BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedTelegramBaseNotificationHandler) {
        this.onReceivedTelegramBaseNotificationHandler = onReceivedTelegramBaseNotificationHandler;
    }

    public void setOnReceivedPaymentNotificationHandler(BaseNotificationHandler<ReceivedPaymentNotification, MessageBody> onReceivedPaymentBaseNotificationHandler) {
        this.onReceivedPaymentBaseNotificationHandler = onReceivedPaymentBaseNotificationHandler;
    }

    public void setOnReceivedSmsNotificationHandler(BaseNotificationHandler<ReceivedSmsNotification, MessageBody> onReceivedSmsBaseNotificationHandler) {
        this.onReceivedSmsBaseNotificationHandler = onReceivedSmsBaseNotificationHandler;
    }

    public void setOnReceivedEmailNotificationHandler(BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedEmailBaseNotificationHandler) {
        this.onReceivedEmailBaseNotificationHandler = onReceivedEmailBaseNotificationHandler;
    }

    public void setOnPaymentStatusNotificationHandler(BaseNotificationHandler<PaymentStatusNotification, MessageBody> onPaymentStatusBaseNotificationHandler) {
        this.onPaymentStatusBaseNotificationHandler = onPaymentStatusBaseNotificationHandler;
    }

    public void setOnReceivedFbMessengerNotificationHandler(BaseNotificationHandler<ReceivedMediaNotification, MessageBody> onReceivedFbMessengerBaseNotificationHandler) {
        this.onReceivedFbMessengerBaseNotificationHandler = onReceivedFbMessengerBaseNotificationHandler;
    }

    public void setOnMessagingSessionStartedNotificationHandler(BaseNotificationHandler<MessagingSessionInitializedNotification, MessageBody> onMessagingSessionStartedBaseNotificationHandler) {
        this.onMessagingSessionStartedBaseNotificationHandler = onMessagingSessionStartedBaseNotificationHandler;
    }

    public void setOnMessagingSessionRenewedNotificationHandler(BaseNotificationHandler<MessagingSessionInitializedNotification, MessageBody> onMessagingSessionRenewedBaseNotificationHandler) {
        this.onMessagingSessionRenewedBaseNotificationHandler = onMessagingSessionRenewedBaseNotificationHandler;
    }

    public void setOnMessagingSessionEndedNotificationHandler(BaseNotificationHandler<MessagingSessionEndedNotification, MessageBody> onMessagingSessionEndedBaseNotificationHandler) {
        this.onMessagingSessionEndedBaseNotificationHandler = onMessagingSessionEndedBaseNotificationHandler;
    }

    public void setOnCustomerActivityNotificationHandler(BaseNotificationHandler<CustomerActivityNotification, MessageBody> onCustomerActivityBaseNotificationHandler) {
        this.onCustomerActivityBaseNotificationHandler = onCustomerActivityBaseNotificationHandler;
    }

    public void setOnMessageStatusNotificationHandler(BaseNotificationHandler<MessageStatusNotification, MessageBody> onMessageStatusBaseNotificationHandler) {
        this.onMessageStatusBaseNotificationHandler = onMessageStatusBaseNotificationHandler;
    }

    public void setOnMessagingConsentUpdateNotificationHandler(BaseNotificationHandler<MessagingConsentUpdateNotification, MessageBody> onMessagingConsentUpdateBaseNotificationHandler) {
        this.onMessagingConsentUpdateBaseNotificationHandler = onMessagingConsentUpdateBaseNotificationHandler;
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


    private void handlePurseNotification(AppSocket.ServerToAppPurseNotification notif, NotificationCallback<MessageBody> callback) {
        if (onPaymentStatusBaseNotificationHandler != null) {
            PaymentStatusNotification payload = new PaymentStatusNotification();
            payload.orgId = notif.getOrgId();
            payload.appId = notif.getAppId();
            payload.purseId = notif.getPurseId();
            payload.createdAt = notif.getCreatedAt().getSeconds();
            payload.transactionId = notif.getPaymentStatus().getTransactionId();
            payload.status = PaymentStatus.valueOf(notif.getPaymentStatus().getStatusValue());
            onPaymentStatusBaseNotificationHandler.handle(payload, null, null, callback);
        } else {
            callback.callback(null, null);
        }
    }

    private void handleCustomerNotification(AppSocket.ServerToAppCustomerNotification notif, NotificationCallback<MessageBody> callback) {
        DataValue appData = null;
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

        if (notif.hasReminder() && onReminderBaseNotificationHandler != null) {

            ReminderNotification payload = Utils.fillInCustomerNotification(notif, new ReminderNotification());
            payload.workId = notif.getReminder().getWorkId().getValue();
            AppModel.CustomerReminder reminder = notif.getReminder().getReminder();
            payload.reminder = new Reminder(reminder.getKey(), reminder.getPayload().getValue(), reminder.getRemindAt().getSeconds(), reminder.getInterval().getSeconds());
            CommonModel.CustomerIndex tag = notif.getReminder().getTag();
            payload.tag = new Tag(tag.getMapping().getKey(), tag.getMapping().getValue().getValue(), tag.getExpiresAt().getSeconds());

            onReminderBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasMessagingSessionStarted() && onMessagingSessionStartedBaseNotificationHandler != null) {

            MessagingSessionInitializedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionInitializedNotification());
            AppSocket.MessagingSessionStartedNotification msg = notif.getMessagingSessionStarted();
            payload.sessionId = msg.getSessionId();
            payload.expiresAt = msg.getExpiresAt().getSeconds();
            payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

            customer.customerNumber = payload.customerNumber;

            onMessagingSessionStartedBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasMessagingSessionRenewed() && onMessagingSessionRenewedBaseNotificationHandler != null) {

            MessagingSessionInitializedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionInitializedNotification());
            AppSocket.MessagingSessionRenewedNotification msg = notif.getMessagingSessionRenewed();
            payload.sessionId = msg.getSessionId();
            payload.expiresAt = msg.getExpiresAt().getSeconds();
            payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

            customer.customerNumber = payload.customerNumber;

            onMessagingSessionRenewedBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasMessagingSessionEnded() && onMessagingSessionEndedBaseNotificationHandler != null) {

            MessagingSessionEndedNotification payload = Utils.fillInCustomerNotification(notif, new MessagingSessionEndedNotification());
            AppSocket.MessagingSessionEndedNotification msg = notif.getMessagingSessionEnded();
            payload.sessionId = msg.getSessionId();
            payload.duration = msg.getDuration().getSeconds();
            payload.reason = MessagingSessionEndReason.valueOf(msg.getReasonValue());
            payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

            customer.customerNumber = payload.customerNumber;

            onMessagingSessionEndedBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasMessagingConsentUpdate() && onMessagingConsentUpdateBaseNotificationHandler != null) {

            MessagingConsentUpdateNotification payload = Utils.fillInCustomerNotification(notif, new MessagingConsentUpdateNotification());
            AppSocket.MessagingConsentUpdateNotification msg = notif.getMessagingConsentUpdate();
            payload.update = ConsentAction.valueOf(msg.getUpdateValue());
            payload.status = MessagingConsentUpdateStatus.valueOf(msg.getStatusValue());
            payload.channelNumber = Utils.makeMessagingChannel(msg.getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(msg.getCustomerNumber());

            customer.customerNumber = payload.customerNumber;

            onMessagingConsentUpdateBaseNotificationHandler.handle(payload, customer, appData, callback);

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
                    if (onReceivedSmsBaseNotificationHandler != null) {
                        onReceivedSmsBaseNotificationHandler.handle(sms, customer, appData, callback);
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
                            callback.callback(body, data);
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
                            callback.callback(body, data);
                        });
                        return;
                    }
                    break;
                case 4: // MessagingChannel.Channel.FB_MESSENGER
                    if (onReceivedFbMessengerBaseNotificationHandler != null) {
                        ReceivedMediaNotification fb_messenger = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                        Utils.fillInMediaMessageNotification(msg, fb_messenger, customer);
                        onReceivedFbMessengerBaseNotificationHandler.handle(fb_messenger, customer, appData, callback);
                        return;
                    }
                    break;
                case 5: // MessagingChannel.Channel.TELEGRAM
                    if (onReceivedTelegramBaseNotificationHandler != null) {
                        ReceivedMediaNotification telegram = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                        Utils.fillInMediaMessageNotification(msg, telegram, customer);
                        onReceivedTelegramBaseNotificationHandler.handle(telegram, customer, appData, callback);
                        return;
                    }
                    break;
                case 6: // MessagingChannel.Channel.WHATSAPP
                    if (onReceivedWhatsappBaseNotificationHandler != null) {
                        ReceivedMediaNotification whatsapp = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                        Utils.fillInMediaMessageNotification(msg, whatsapp, customer);
                        onReceivedWhatsappBaseNotificationHandler.handle(whatsapp, customer, appData, callback);
                        return;
                    }
                    break;
                case 7: // MessagingChannel.Channel.EMAIL
                    if (onReceivedEmailBaseNotificationHandler != null) {
                        ReceivedMediaNotification email = Utils.fillInCustomerNotification(notif, new ReceivedMediaNotification());
                        Utils.fillInMediaMessageNotification(msg, email, customer);
                        onReceivedEmailBaseNotificationHandler.handle(email, customer, appData, callback);
                        return;
                    }
                    break;
            }
            callback.callback(null, appData);

        } else if (notif.hasMessageStatus() && onMessageStatusBaseNotificationHandler != null) {

            MessageStatusNotification payload = Utils.fillInCustomerNotification(notif, new MessageStatusNotification());
            payload.messageId = notif.getMessageStatus().getMessageId();
            payload.status = MessageDeliveryStatus.valueOf(notif.getMessageStatus().getStatusValue());

            onMessageStatusBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasSentMessageReaction() && onSentMessageReactionBaseNotificationHandler != null) {

            SentMessageReactionNotification payload = Utils.fillInCustomerNotification(notif, new SentMessageReactionNotification());

            payload.messageId = notif.getSentMessageReaction().getMessageId();
            payload.channelNumber = Utils.makeMessagingChannel(notif.getSentMessageReaction().getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(notif.getSentMessageReaction().getCustomerNumber());
            payload.reaction = MessageReaction.valueOf(notif.getSentMessageReaction().getReactionValue());

            customer.customerNumber = payload.customerNumber;

            onSentMessageReactionBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasReceivedPayment() && onReceivedPaymentBaseNotificationHandler != null) {

            ReceivedPaymentNotification payload = Utils.fillInCustomerNotification(notif, new ReceivedPaymentNotification());

            payload.purseId = notif.getReceivedPayment().getPurseId();
            payload.transactionId = notif.getReceivedPayment().getTransactionId();
            payload.channelNumber = Utils.makePaymentChannel(notif.getReceivedPayment().getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(notif.getCustomerActivity().getCustomerNumber());
            payload.status = PaymentStatus.valueOf(notif.getReceivedPayment().getStatus().getNumber());
            payload.value = new Cash(notif.getReceivedPayment().getValue().getCurrencyCode(), notif.getReceivedPayment().getValue().getAmount());

            customer.customerNumber = payload.customerNumber;

            onReceivedPaymentBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasPaymentStatus() && onPaymentStatusBaseNotificationHandler != null) {

            PaymentStatusNotification payload = Utils.fillInCustomerNotification(notif, new PaymentStatusNotification());
            payload.transactionId = notif.getPaymentStatus().getTransactionId();
            payload.status = PaymentStatus.valueOf(notif.getPaymentStatus().getStatusValue());

            onPaymentStatusBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasWalletPaymentStatus() && onWalletPaymentStatusBaseNotificationHandler != null) {

            WalletPaymentStatusNotification payload = Utils.fillInCustomerNotification(notif, new WalletPaymentStatusNotification());
            payload.walletId = notif.getWalletPaymentStatus().getWalletId();
            payload.transactionId = notif.getWalletPaymentStatus().getTransactionId();
            payload.status = PaymentStatus.valueOf(notif.getWalletPaymentStatus().getStatusValue());

            onWalletPaymentStatusBaseNotificationHandler.handle(payload, customer, appData, callback);

        } else if (notif.hasCustomerActivity() && onCustomerActivityBaseNotificationHandler != null) {

            CustomerActivityNotification payload = Utils.fillInCustomerNotification(notif, new CustomerActivityNotification());
            payload.sessionId = notif.getCustomerActivity().getSessionId();
            payload.activity = new Activity(
                    notif.getCustomerActivity().getActivity().getKey(),
                    notif.getCustomerActivity().getActivity().getPropertiesMap(),
                    notif.getCustomerActivity().getSessionId(),
                    notif.getCustomerActivity().getActivity().getCreatedAt().getSeconds()
            );
            payload.channelNumber = Utils.makeActivityChannel(notif.getCustomerActivity().getChannelNumber());
            payload.customerNumber = Utils.makeCustomerNumber(notif.getCustomerActivity().getCustomerNumber());

            customer.customerNumber = payload.customerNumber;

            onCustomerActivityBaseNotificationHandler.handle(payload, customer, appData, callback);
        } else {
            callback.callback(null, appData);
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
                .setAuthToken(StringValue.newBuilder().setValue(opts.authToken));

        builder.setSimplexMode(true);
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
