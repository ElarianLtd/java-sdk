package com.elarian;

import com.elarian.hera.proto.ActivityModel;
import com.elarian.hera.proto.AppSocket;
import com.elarian.hera.proto.CommonModel;
import com.elarian.hera.proto.MessagingModel;
import com.elarian.hera.proto.MessagingStateOuterClass;
import com.elarian.hera.proto.PaymentModel;
import com.elarian.model.ActiveMessagingChannelState;
import com.elarian.model.ActivityChannel;
import com.elarian.model.BlockedMessagingChannelState;
import com.elarian.model.Cash;
import com.elarian.model.ChannelMessage;
import com.elarian.model.CompleteMessagingSession;
import com.elarian.model.CustomerNumber;
import com.elarian.model.Dequeue;
import com.elarian.model.Dial;
import com.elarian.model.Email;
import com.elarian.model.Enqueue;
import com.elarian.model.GetDigits;
import com.elarian.model.GetRecording;
import com.elarian.model.InSessionMessagingChannelState;
import com.elarian.model.Location;
import com.elarian.model.Media;
import com.elarian.model.Message;
import com.elarian.model.MessageBody;
import com.elarian.model.MessageDeliveryStatus;
import com.elarian.model.MessageReaction;
import com.elarian.model.MessageReactionState;
import com.elarian.model.MessageReplyToken;
import com.elarian.model.MessagingChannel;
import com.elarian.model.MessagingChannelState;
import com.elarian.model.MessagingSessionEndReason;
import com.elarian.model.Notification;
import com.elarian.model.PaymentChannel;
import com.elarian.model.PaymentChannelCounterParty;
import com.elarian.model.PaymentCounterParty;
import com.elarian.model.PaymentCustomerCounterParty;
import com.elarian.model.PaymentPurseCounterParty;
import com.elarian.model.PaymentState;
import com.elarian.model.PaymentStatus;
import com.elarian.model.PaymentWalletCounterParty;
import com.elarian.model.Play;
import com.elarian.model.PromptMessageReplyAction;
import com.elarian.model.ReceivedMediaNotification;
import com.elarian.model.ReceivedMessage;
import com.elarian.model.RecordSession;
import com.elarian.model.Redirect;
import com.elarian.model.Reject;
import com.elarian.model.ReplyTokenPrompt;
import com.elarian.model.ReplyTokenPromptMenu;
import com.elarian.model.Say;
import com.elarian.model.SentMessage;
import com.elarian.model.Template;
import com.elarian.model.TextToSpeechVoice;
import com.elarian.model.UssdInput;
import com.elarian.model.UssdMenu;
import com.elarian.model.VoiceCallDialInput;
import com.elarian.model.VoiceCallDirection;
import com.elarian.model.VoiceCallHangupCause;
import com.elarian.model.VoiceCallInput;
import com.elarian.model.VoiceCallQueueInput;
import com.elarian.model.VoiceCallStatus;
import com.google.protobuf.Duration;
import com.google.protobuf.Int32Value;
import com.google.protobuf.StringValue;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

class Utils {

    public static <T extends Notification> T fillInCustomerNotification(AppSocket.ServerToAppCustomerNotification notif, T target) {
        target.orgId = notif.getOrgId();
        target.appId = notif.getAppId();
        target.customerId = notif.getCustomerId();
        target.createdAt = notif.getCreatedAt().getSeconds();
        return target;
    }

    public static ReceivedMediaNotification fillInMediaMessageNotification(AppSocket.ReceivedMessageNotification notif, ReceivedMediaNotification target, Customer targetCustomer) {
        target.messageId = notif.getMessageId();
        target.sessionId = notif.getSessionId().getValue();
        target.channelNumber = Utils.makeMessagingChannel(notif.getChannelNumber());
        target.customerNumber = Utils.makeCustomerNumber(notif.getCustomerNumber());

        targetCustomer.customerNumber = target.customerNumber;

        List<MessagingModel.InboundMessageBody> parts = notif.getPartsList();
        for (MessagingModel.InboundMessageBody part: parts) {
            target.text = part.getText();

            if (part.hasMedia()) {
                target.media = new Media(part.getMedia().getUrl(), Media.MediaType.valueOf(part.getMedia().getMediaValue()));
            }

            if (part.hasEmail()) {
                target.email = new Email(
                        part.getEmail().getSubject(),
                        part.getEmail().getBodyPlain(),
                        part.getEmail().getBodyHtml()
                );
                target.email.cc = part.getEmail().getCcListList();
                target.email.bcc = part.getEmail().getBccListList();
                target.email.attachments = part.getEmail().getAttachmentsList();
            }

            if (part.hasLocation()) {
                target.location = new Location(
                        part.getLocation().getLatitude(),
                        part.getLocation().getLongitude(),
                        part.getLocation().getLabel().getValue(),
                        part.getLocation().getAddress().getValue()
                );
            }
        }
        return target;
    }

    public static ActivityChannel makeActivityChannel(ActivityModel.ActivityChannelNumber num) {
        return new ActivityChannel(num.getNumber(), ActivityChannel.Channel.valueOf(num.getChannelValue()));
    }

    public static PaymentChannel makePaymentChannel(PaymentModel.PaymentChannelNumber num) {
        return new PaymentChannel(num.getNumber(), PaymentChannel.Channel.valueOf(num.getChannelValue()));
    }

    public static MessagingChannel makeMessagingChannel(MessagingModel.MessagingChannelNumber num) {
        return new MessagingChannel(num.getNumber(), MessagingChannel.Channel.valueOf(num.getChannelValue()));
    }

    public static CustomerNumber makeCustomerNumber(CommonModel.CustomerNumber num) {
        return new CustomerNumber(num.getNumber(), CustomerNumber.Provider.valueOf(num.getProviderValue()));
    }

    public static PaymentState.PaymentTransaction makeTransactionLog(PaymentModel.PaymentTransaction transaction) {
        PaymentState.PaymentTransaction txn = new PaymentState.PaymentTransaction();
        txn.appId = transaction.getAppId().getValue();
        txn.transactionId = transaction.getTransactionId();
        txn.value = new Cash(transaction.getValue().getCurrencyCode(), transaction.getValue().getAmount());
        txn.status = PaymentStatus.valueOf(transaction.getStatusValue());
        txn.updatedAt = transaction.getUpdatedAt().getSeconds();
        txn.createdAt = transaction.getCreatedAt().getSeconds();

        Function<PaymentModel.PaymentCounterParty, PaymentCounterParty> extractPaymentCounterParty = (party) -> {
            PaymentCounterParty result = null;
            if (party.hasWallet()) {
                PaymentModel.PaymentWalletCounterParty wallet = party.getWallet();
                result = new PaymentCounterParty(new PaymentWalletCounterParty(wallet.getCustomerId(), wallet.getWalletId()));
            } else if (party.hasChannel()) {
                PaymentModel.PaymentChannelCounterParty channel = party.getChannel();
                result = new PaymentCounterParty(new PaymentChannelCounterParty(
                        makePaymentChannel(channel.getChannelNumber()),
                        channel.getAccount().getValue(),
                        channel.getChannelCode()));
            } else if (party.hasCustomer()) {
                PaymentModel.PaymentCustomerCounterParty customer = party.getCustomer();
                result = new PaymentCounterParty(new PaymentCustomerCounterParty(
                        makeCustomerNumber(customer.getCustomerNumber()),
                        makePaymentChannel(customer.getChannelNumber())));
            } else if (party.hasPurse()) {
                result = new PaymentCounterParty(new PaymentPurseCounterParty(party.getPurse().getPurseId()));
            }
            return result;
        };


        if (transaction.hasDebitParty()) {
            txn.debitParty = extractPaymentCounterParty.apply(transaction.getDebitParty());
        }

        if (transaction.hasCreditParty()) {
            txn.creditParty = extractPaymentCounterParty.apply(transaction.getCreditParty());
        }

        return txn;
    }

    public static MessagingChannelState makeMessagingChannelState(MessagingStateOuterClass.MessagingChannelState state) {
        MessagingChannelState ch = new MessagingChannelState();

        if (state.hasActive()) {
            MessagingStateOuterClass.ActiveMessagingChannelState chState = state.getActive();
            ch.active = new ActiveMessagingChannelState();
            ch.active.allowedAt = chState.getAllowedAt().getSeconds();
            ch.active.customerNumber = makeCustomerNumber(chState.getCustomerNumber());
            ch.active.channelNumber = makeMessagingChannel(chState.getChannelNumber());
            ch.active.replyToken = new MessageReplyToken(chState.getReplyToken().getToken(), chState.getReplyToken().getExpiresAt().getSeconds());
            ch.active.messages = chState.getMessagesList()
                    .stream()
                    .map((item) -> {
                        if (item.hasReceived()) {
                            MessagingStateOuterClass.ReceivedMessage received = item.getReceived();
                            ReceivedMessage target = new ReceivedMessage();
                            target.cost = new Cash(received.getCost().getCurrencyCode(), received.getCost().getAmount());
                            target.messageId = received.getMessageId();
                            target.createdAt = received.getCreatedAt().getSeconds();
                            target.sessionId = received.getSessionId().getValue();
                            target.inReplyTo = received.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(received.getProviderValue());
                            target.appId = received.getAppId().getValue();

                            received.getPartsList().forEach((part) -> {
                                if (!part.getText().isEmpty()) {
                                    target.texts.add(part.getText());
                                }
                                if (part.hasMedia()) {
                                    target.media.add(new Media(part.getMedia().getUrl(), Media.MediaType.valueOf(part.getMedia().getMediaValue())));
                                }

                                if (part.hasLocation()) {
                                    Location loc = new Location(
                                            part.getLocation().getLatitude(),
                                            part.getLocation().getLongitude(),
                                            part.getLocation().getLabel().getValue(),
                                            part.getLocation().getAddress().getValue()
                                    );
                                    target.locations.add(loc);
                                }

                                if (part.hasEmail()) {
                                    Email email = new Email(
                                            part.getEmail().getSubject(),
                                            part.getEmail().getBodyPlain(),
                                            part.getEmail().getBodyHtml()
                                    );
                                    email.cc = part.getEmail().getCcListList();
                                    email.bcc = part.getEmail().getBccListList();
                                    email.attachments = part.getEmail().getAttachmentsList();
                                    target.emails.add(email);
                                }

                                if (part.hasUssd()) {
                                    UssdInput ussdInput = new UssdInput(
                                            part.getUssd().getText().getValue(),
                                            UssdInput.UssdStatus.valueOf(part.getUssd().getStatusValue())
                                    );
                                    target.ussd.add(ussdInput);
                                }

                                if (part.hasVoice()) {
                                    target.voice.add(makeVoiceCallInput(part.getVoice()));
                                }

                            });

                            return new ChannelMessage(target);
                        }

                        if (item.hasSent()) {
                            MessagingStateOuterClass.SentMessage sent = item.getSent();
                            SentMessage target = new SentMessage();
                            target.cost = new Cash(sent.getCost().getCurrencyCode(), sent.getCost().getAmount());
                            target.messageId = sent.getMessageId();
                            target.createdAt = sent.getCreatedAt().getSeconds();
                            target.sessionId = sent.getSessionId().getValue();
                            target.inReplyTo = sent.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(sent.getProviderValue());
                            target.appId = sent.getAppId().getValue();
                            target.message = makeMessage(sent.getMessage());
                            target.updatedAt = sent.getUpdatedAt().getSeconds();
                            target.status = MessageDeliveryStatus.valueOf(sent.getStatusValue());
                            target.reaction = sent.getReactionsList()
                                    .stream()
                                    .map((reaction) -> new MessageReactionState(
                                            MessageReaction.valueOf(reaction.getReactionValue()),
                                            reaction.getCreatedAt().getSeconds()
                                    ))
                                    .collect(Collectors.toList());
                            return new ChannelMessage(target);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            ch.active.sessions = chState.getSessionsList()
                    .stream()
                    .map((item) -> {
                        CompleteMessagingSession session = new CompleteMessagingSession();
                        session.sessionId = item.getSessionId();
                        session.appIds = item.getAppIdsList();
                        session.duration = item.getDuration().getSeconds();
                        session.startedAt = item.getStartedAt().getSeconds();
                        session.cost = new Cash(item.getCost().getCurrencyCode(), item.getCost().getAmount());
                        session.endReason = MessagingSessionEndReason.valueOf(item.getEndReasonValue());
                        return session;
                    })
                    .collect(Collectors.toList());
        }

        if (state.hasBlocked()) {
            MessagingStateOuterClass.BlockedMessagingChannelState chState = state.getBlocked();
            ch.blocked = new BlockedMessagingChannelState();
            ch.blocked.blockedAt = chState.getBlockedAt().getSeconds();
            ch.blocked.customerNumber = makeCustomerNumber(chState.getCustomerNumber());
            ch.blocked.channelNumber = makeMessagingChannel(chState.getChannelNumber());
            ch.blocked.replyToken = new MessageReplyToken(chState.getReplyToken().getToken(), chState.getReplyToken().getExpiresAt().getSeconds());
            ch.blocked.messages = chState.getMessagesList()
                    .stream()
                    .map((item) -> {
                        if (item.hasReceived()) {
                            MessagingStateOuterClass.ReceivedMessage received = item.getReceived();
                            ReceivedMessage target = new ReceivedMessage();
                            target.cost = new Cash(received.getCost().getCurrencyCode(), received.getCost().getAmount());
                            target.messageId = received.getMessageId();
                            target.createdAt = received.getCreatedAt().getSeconds();
                            target.sessionId = received.getSessionId().getValue();
                            target.inReplyTo = received.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(received.getProviderValue());
                            target.appId = received.getAppId().getValue();

                            received.getPartsList().forEach((part) -> {
                                if (!part.getText().isEmpty()) {
                                    target.texts.add(part.getText());
                                }
                                if (part.hasMedia()) {
                                    target.media.add(new Media(part.getMedia().getUrl(), Media.MediaType.valueOf(part.getMedia().getMediaValue())));
                                }

                                if (part.hasLocation()) {
                                    Location loc = new Location(
                                            part.getLocation().getLatitude(),
                                            part.getLocation().getLongitude(),
                                            part.getLocation().getLabel().getValue(),
                                            part.getLocation().getAddress().getValue()
                                    );
                                    target.locations.add(loc);
                                }

                                if (part.hasEmail()) {
                                    Email email = new Email(
                                            part.getEmail().getSubject(),
                                            part.getEmail().getBodyPlain(),
                                            part.getEmail().getBodyHtml()
                                    );
                                    email.cc = part.getEmail().getCcListList();
                                    email.bcc = part.getEmail().getBccListList();
                                    email.attachments = part.getEmail().getAttachmentsList();
                                    target.emails.add(email);
                                }

                                if (part.hasUssd()) {
                                    UssdInput ussdInput = new UssdInput(
                                            part.getUssd().getText().getValue(),
                                            UssdInput.UssdStatus.valueOf(part.getUssd().getStatusValue())
                                    );
                                    target.ussd.add(ussdInput);
                                }

                                if (part.hasVoice()) {
                                    target.voice.add(makeVoiceCallInput(part.getVoice()));
                                }

                            });

                            return new ChannelMessage(target);
                        }

                        if (item.hasSent()) {
                            MessagingStateOuterClass.SentMessage sent = item.getSent();
                            SentMessage target = new SentMessage();
                            target.cost = new Cash(sent.getCost().getCurrencyCode(), sent.getCost().getAmount());
                            target.messageId = sent.getMessageId();
                            target.createdAt = sent.getCreatedAt().getSeconds();
                            target.sessionId = sent.getSessionId().getValue();
                            target.inReplyTo = sent.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(sent.getProviderValue());
                            target.appId = sent.getAppId().getValue();
                            target.message = makeMessage(sent.getMessage());
                            target.updatedAt = sent.getUpdatedAt().getSeconds();
                            target.status = MessageDeliveryStatus.valueOf(sent.getStatusValue());
                            target.reaction = sent.getReactionsList()
                                    .stream()
                                    .map((reaction) -> new MessageReactionState(
                                            MessageReaction.valueOf(reaction.getReactionValue()),
                                            reaction.getCreatedAt().getSeconds()
                                    ))
                                    .collect(Collectors.toList());
                            return new ChannelMessage(target);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            ch.blocked.sessions = chState.getSessionsList()
                    .stream()
                    .map((item) -> {
                        CompleteMessagingSession session = new CompleteMessagingSession();
                        session.sessionId = item.getSessionId();
                        session.appIds = item.getAppIdsList();
                        session.duration = item.getDuration().getSeconds();
                        session.startedAt = item.getStartedAt().getSeconds();
                        session.cost = new Cash(item.getCost().getCurrencyCode(), item.getCost().getAmount());
                        session.endReason = MessagingSessionEndReason.valueOf(item.getEndReasonValue());
                        return session;
                    })
                    .collect(Collectors.toList());
        }

        if (state.hasInSession()) {
            MessagingStateOuterClass.InSessionMessagingChannelState chState = state.getInSession();
            ch.inSession = new InSessionMessagingChannelState();
            ch.inSession.sessionId = chState.getSessionId();
            ch.inSession.appIds = chState.getAppIdsList();
            ch.inSession.startedAt = chState.getStartedAt().getSeconds();
            ch.inSession.expiresAt = chState.getExpiresAt().getSeconds();
            ch.inSession.allowedAt = chState.getAllowedAt().getSeconds();
            ch.inSession.customerNumber = makeCustomerNumber(chState.getCustomerNumber());
            ch.inSession.channelNumber = makeMessagingChannel(chState.getChannelNumber());
            ch.inSession.replyToken = new MessageReplyToken(chState.getReplyToken().getToken(), chState.getReplyToken().getExpiresAt().getSeconds());
            ch.inSession.messages = chState.getMessagesList()
                    .stream()
                    .map((item) -> {
                        if (item.hasReceived()) {
                            MessagingStateOuterClass.ReceivedMessage received = item.getReceived();
                            ReceivedMessage target = new ReceivedMessage();
                            target.cost = new Cash(received.getCost().getCurrencyCode(), received.getCost().getAmount());
                            target.messageId = received.getMessageId();
                            target.createdAt = received.getCreatedAt().getSeconds();
                            target.sessionId = received.getSessionId().getValue();
                            target.inReplyTo = received.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(received.getProviderValue());
                            target.appId = received.getAppId().getValue();

                            received.getPartsList().forEach((part) -> {
                                if (!part.getText().isEmpty()) {
                                    target.texts.add(part.getText());
                                }
                                if (part.hasMedia()) {
                                    target.media.add(new Media(part.getMedia().getUrl(), Media.MediaType.valueOf(part.getMedia().getMediaValue())));
                                }

                                if (part.hasLocation()) {
                                    Location loc = new Location(
                                            part.getLocation().getLatitude(),
                                            part.getLocation().getLongitude(),
                                            part.getLocation().getLabel().getValue(),
                                            part.getLocation().getAddress().getValue()
                                    );
                                    target.locations.add(loc);
                                }

                                if (part.hasEmail()) {
                                    Email email = new Email(
                                            part.getEmail().getSubject(),
                                            part.getEmail().getBodyPlain(),
                                            part.getEmail().getBodyHtml()
                                    );
                                    email.cc = part.getEmail().getCcListList();
                                    email.bcc = part.getEmail().getBccListList();
                                    email.attachments = part.getEmail().getAttachmentsList();
                                    target.emails.add(email);
                                }

                                if (part.hasUssd()) {
                                    UssdInput ussdInput = new UssdInput(
                                            part.getUssd().getText().getValue(),
                                            UssdInput.UssdStatus.valueOf(part.getUssd().getStatusValue())
                                    );
                                    target.ussd.add(ussdInput);
                                }

                                if (part.hasVoice()) {
                                    target.voice.add(makeVoiceCallInput(part.getVoice()));
                                }

                            });

                            return new ChannelMessage(target);
                        }

                        if (item.hasSent()) {
                            MessagingStateOuterClass.SentMessage sent = item.getSent();
                            SentMessage target = new SentMessage();
                            target.cost = new Cash(sent.getCost().getCurrencyCode(), sent.getCost().getAmount());
                            target.messageId = sent.getMessageId();
                            target.createdAt = sent.getCreatedAt().getSeconds();
                            target.sessionId = sent.getSessionId().getValue();
                            target.inReplyTo = sent.getInReplyTo().getValue();
                            target.provider = CustomerNumber.Provider.valueOf(sent.getProviderValue());
                            target.appId = sent.getAppId().getValue();
                            target.message = makeMessage(sent.getMessage());
                            target.updatedAt = sent.getUpdatedAt().getSeconds();
                            target.status = MessageDeliveryStatus.valueOf(sent.getStatusValue());
                            target.reaction = sent.getReactionsList()
                                    .stream()
                                    .map((reaction) -> new MessageReactionState(
                                            MessageReaction.valueOf(reaction.getReactionValue()),
                                            reaction.getCreatedAt().getSeconds()
                                    ))
                                    .collect(Collectors.toList());
                            return new ChannelMessage(target);
                        }

                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            ch.inSession.sessions = chState.getSessionsList()
                    .stream()
                    .map((item) -> {
                        CompleteMessagingSession session = new CompleteMessagingSession();
                        session.sessionId = item.getSessionId();
                        session.appIds = item.getAppIdsList();
                        session.duration = item.getDuration().getSeconds();
                        session.startedAt = item.getStartedAt().getSeconds();
                        session.cost = new Cash(item.getCost().getCurrencyCode(), item.getCost().getAmount());
                        session.endReason = MessagingSessionEndReason.valueOf(item.getEndReasonValue());
                        return session;
                    })
                    .collect(Collectors.toList());
        }

        return ch;
    }

    public static VoiceCallInput makeVoiceCallInput(MessagingModel.VoiceCallInputMessageBody voice) {
        VoiceCallInput input = new VoiceCallInput();

        input.startedAt = voice.getStartedAt().getSeconds();
        input.dtmfDigits = voice.getDtmfDigits().getValue();
        input.recordingUrl = voice.getRecordingUrl().getValue();
        input.status = VoiceCallStatus.valueOf(voice.getStatusValue());
        input.direction = VoiceCallDirection.valueOf(voice.getDirectionValue());
        input.hangupCause = VoiceCallHangupCause.valueOf(voice.getHangupCauseValue());

        input.dialData = new VoiceCallDialInput();
        input.dialData.destinationNumber = voice.getDialData().getDestinationNumber();
        input.dialData.duration = voice.getDialData().getDuration().getSeconds();
        input.dialData.startedAt = voice.getDialData().getStartedAt().getSeconds();

        input.queueData = new VoiceCallQueueInput();
        input.queueData.dequeuedAt = voice.getQueueData().getDequeuedAt().getSeconds();
        input.queueData.enqueuedAt = voice.getQueueData().getEnqueuedAt().getSeconds();
        input.queueData.queueDuration = voice.getQueueData().getQueueDuration().getSeconds();
        input.queueData.dequeuedToSessionId = voice.getQueueData().getDequeuedToSessionId().getValue();
        input.queueData.dequeuedToNumber = voice.getQueueData().getDequeuedToNumber().getValue();

        return input;
    }

    public static PaymentModel.PaymentCounterParty buildPaymentCounterParty(PaymentCounterParty counterParty) {

        PaymentModel.PaymentCounterParty.Builder result = PaymentModel.PaymentCounterParty.newBuilder();

        if (counterParty.customer != null) {
            PaymentCustomerCounterParty party = counterParty.customer;
            result.setCustomer(PaymentModel.PaymentCustomerCounterParty
                    .newBuilder()
                    .setCustomerNumber(CommonModel.CustomerNumber
                            .newBuilder()
                            .setNumber(party.customerNumber.number)
                            .setProviderValue(party.customerNumber.provider.getValue())
                            .build())
                    .setChannelNumber(PaymentModel.PaymentChannelNumber
                            .newBuilder()
                            .setNumber(party.channelNumber.number)
                            .setChannelValue(party.channelNumber.channel.getValue())
                            .build())
                    .build());
        } else if (counterParty.purse != null) {
            PaymentPurseCounterParty party = counterParty.purse;
            result.setPurse(PaymentModel.PaymentPurseCounterParty
                    .newBuilder()
                    .setPurseId(party.purseId)
                    .build());
        } else if (counterParty.channel != null) {
            PaymentChannelCounterParty party = counterParty.channel;
            result.setChannel(PaymentModel.PaymentChannelCounterParty
                    .newBuilder()
                    .setAccount(StringValue.of(party.account))
                    .setChannelCode(party.networkCode)
                    .setChannelNumber(PaymentModel.PaymentChannelNumber
                        .newBuilder()
                        .setNumber(party.channelNumber.number)
                        .setChannelValue(party.channelNumber.channel.getValue())
                        .build())
                    .build());
        } else if (counterParty.wallet != null) {
            PaymentWalletCounterParty party = counterParty.wallet;
            result.setWallet(PaymentModel.PaymentWalletCounterParty
                    .newBuilder()
                    .setCustomerId(party.customerId)
                    .setWalletId(party.walletId)
                    .build());
        }
        return result.build();
    }

    public static MessagingModel.OutboundMessage buildOutgoingMessage(Message message) {

        MessagingModel.OutboundMessage.Builder outgoing = MessagingModel.OutboundMessage.newBuilder();
        MessagingModel.OutboundMessageBody.Builder body = MessagingModel.OutboundMessageBody.newBuilder();

        outgoing.addAllLabels(message.labels);
        if (message.providerTag != null) {
            outgoing.setProviderTag(StringValue.of(message.providerTag));
        }

        if (message.replyPrompt != null) {
            outgoing.setReplyPrompt(MessagingModel.OutboundMessageReplyPrompt
                    .newBuilder()
                    .setActionValue(message.replyPrompt.action.getValue())
                    .addAllMenu(message.replyPrompt.menu.stream().map(menu -> {
                        MessagingModel.PromptMessageMenuItemBody.Builder item = MessagingModel.PromptMessageMenuItemBody.newBuilder();
                        if (menu.text != null) {
                            item.setText(menu.text);
                        } else if (menu.media != null) {
                            item.setMedia(
                                    MessagingModel.MediaMessageBody
                                            .newBuilder()
                                            .setUrl(menu.media.url)
                                            .setMediaValue(menu.media.type.getValue())
                                            .build()
                            );
                        }
                        return item.build();
                    }).collect(Collectors.toList()))
                    .build());
        }

        MessageBody messageBody = new MessageBody();
        if (message.body != null) {
            messageBody = message.body;
        }

        if (messageBody.text != null) {
            body.setText(messageBody.text);
        } else if (messageBody.media != null) {
            body.setMedia(MessagingModel.MediaMessageBody
                    .newBuilder()
                    .setUrl(messageBody.media.url)
                    .setMediaValue(messageBody.media.type.getValue())
                    .build());
        } else if (messageBody.location != null) {
            body.setLocation(MessagingModel.LocationMessageBody
                    .newBuilder()
                    .setAddress(StringValue.of(messageBody.location.address))
                    .setLabel(StringValue.of(messageBody.location.label))
                    .setLatitude(messageBody.location.latitude)
                    .setLongitude(messageBody.location.longitude)
                    .build());
        } else if (messageBody.email != null) {
            body.setEmail(MessagingModel
                    .EmailMessageBody
                    .newBuilder()
                    .setSubject(messageBody.email.subject)
                    .setBodyPlain(messageBody.email.plain)
                    .setBodyHtml(messageBody.email.html)
                    .addAllCcList(messageBody.email.cc)
                    .addAllBccList(messageBody.email.bcc)
                    .addAllAttachments(messageBody.email.attachments)
                    .build());
        } else if (messageBody.template != null) {
            MessagingModel.TemplateMessageBody.Builder template = MessagingModel.TemplateMessageBody.newBuilder();
            template.setId(messageBody.template.id);
            template.putAllParams(messageBody.template.params);
            body.setTemplate(template);
        } else if(messageBody.url != null) {
            body.setUrl(messageBody.url);
        } else if (messageBody.voice != null && !messageBody.voice.isEmpty()) {
            body.setVoice(MessagingModel.VoiceCallDialplanMessageBody
                    .newBuilder()
                    .addAllActions(
                            messageBody.voice
                                    .stream()
                                    .map(action -> {

                                        MessagingModel.VoiceCallAction.Builder callAction = MessagingModel.VoiceCallAction
                                                .newBuilder();

                                        if (action instanceof Say) {
                                            Say say = (Say) action;
                                            callAction.setSay(MessagingModel.SayCallAction
                                                    .newBuilder()
                                                    .setText(say.text)
                                                    .setPlayBeep(say.playBeep)
                                                    .setVoiceValue(say.voice.getValue())
                                                    .build());
                                        } else if (action instanceof Play) {
                                            Play play = (Play) action;
                                            callAction.setPlay(MessagingModel.PlayCallAction
                                                    .newBuilder()
                                                    .setUrl(play.url)
                                                    .build());
                                        } else if (action instanceof GetDigits) {
                                            GetDigits getDigits = (GetDigits) action;

                                            MessagingModel.GetDigitsCallAction.Builder builder = MessagingModel.GetDigitsCallAction
                                                    .newBuilder()
                                                    .setTimeout(Duration.newBuilder().setSeconds(getDigits.timeout))
                                                    .setFinishOnKey(StringValue.of(getDigits.finishOnKey))
                                                    .setNumDigits(Int32Value.newBuilder().setValue(getDigits.numDigits));
                                            if (getDigits.say != null) {
                                                builder.setSay(
                                                        MessagingModel.SayCallAction
                                                                .newBuilder()
                                                                .setText(getDigits.say.text)
                                                                .setPlayBeep(getDigits.say.playBeep)
                                                                .setVoiceValue(getDigits.say.voice.getValue())
                                                                .build()
                                                );
                                            } else if (getDigits.play != null) {
                                                builder.setPlay(
                                                        MessagingModel.PlayCallAction
                                                                .newBuilder()
                                                                .setUrl(getDigits.play.url)
                                                                .build()
                                                );
                                            }
                                            callAction.setGetDigits(builder);
                                        } else if (action instanceof Dial) {
                                            Dial dial = (Dial) action;
                                            callAction.setDial(MessagingModel.DialCallAction
                                                    .newBuilder()
                                                    .setRecord(dial.record)
                                                    .setSequential(dial.sequential)
                                                    .setRingbackTone(StringValue.of(dial.ringbackTone))
                                                    .setCallerId(StringValue.of(dial.callerId))
                                                    .setMaxDuration(Int32Value.newBuilder().setValue(dial.maxDuration).build())
                                                    .addAllCustomerNumbers(dial.customerNumbers
                                                            .stream()
                                                            .map(i -> CommonModel.CustomerNumber
                                                                    .newBuilder()
                                                                    .setNumber(i.number)
                                                                    .setProviderValue(i.provider.getValue())
                                                                    .build())
                                                            .collect(Collectors.toList()))
                                                    .build());
                                        } else if (action instanceof RecordSession) {
                                            callAction.setRecordSession(MessagingModel.RecordSessionCallAction.newBuilder().build());
                                        } else if (action instanceof GetRecording) {
                                            GetRecording getRecording = (GetRecording) action;

                                            MessagingModel.GetRecordingCallAction.Builder builder = MessagingModel.GetRecordingCallAction
                                                    .newBuilder()
                                                    .setTimeout(Duration.newBuilder().setSeconds(getRecording.timeout))
                                                    .setMaxLength(Duration.newBuilder().setSeconds(getRecording.maxLength))
                                                    .setFinishOnKey(StringValue.of(getRecording.finishOnKey))
                                                    .setPlayBeep(getRecording.playBeep)
                                                    .setTrimSilence(getRecording.trimSilence);

                                            if (getRecording.say != null) {
                                                builder.setSay(
                                                        MessagingModel.SayCallAction
                                                                .newBuilder()
                                                                .setText(getRecording.say.text)
                                                                .setPlayBeep(getRecording.say.playBeep)
                                                                .setVoiceValue(getRecording.say.voice.getValue())
                                                                .build()
                                                );
                                            } else if (getRecording.play != null) {
                                                builder.setPlay(
                                                        MessagingModel.PlayCallAction
                                                                .newBuilder()
                                                                .setUrl(getRecording.play.url)
                                                                .build()
                                                );
                                            }
                                            callAction.setGetRecording(builder);
                                        } else if (action instanceof Enqueue) {
                                            Enqueue enqueue = (Enqueue) action;
                                            callAction.setEnqueue(MessagingModel.EnqueueCallAction
                                                    .newBuilder()
                                                    .setHoldMusic(StringValue.of(enqueue.holdMusic))
                                                    .setQueueName(StringValue.of(enqueue.queueName))
                                                    .build());
                                        } else if (action instanceof Dequeue) {
                                            Dequeue dequeue = (Dequeue) action;
                                            callAction.setDequeue(MessagingModel.DequeueCallAction
                                                    .newBuilder()
                                                    .setQueueName(StringValue.of(dequeue.queueName))
                                                    .setChannelNumber(MessagingModel.MessagingChannelNumber
                                                            .newBuilder()
                                                            .setNumber(dequeue.channelNumber.number)
                                                            .setChannelValue(dequeue.channelNumber.channel.getValue())
                                                            .build())
                                                    .build());
                                        } else if (action instanceof Reject) {
                                            callAction.setReject(MessagingModel.RejectCallAction.newBuilder().build());
                                        } else if (action instanceof Redirect) {
                                            callAction.setRedirect(MessagingModel.RedirectCallAction
                                                    .newBuilder()
                                                    .setUrl(((Redirect) action).url)
                                                    .build());
                                        }

                                        return callAction.build();
                                    })
                                    .collect(Collectors.toList()))
                    .build());
        } else if (messageBody.ussd != null) {
            body.setUssd(MessagingModel.UssdMenuMessageBody
                    .newBuilder()
                    .setText(messageBody.ussd.text)
                    .setIsTerminal(messageBody.ussd.isTerminal)
                    .build());
        }
        outgoing.setBody(body);

        return outgoing.build();
    }

    public static Message makeMessage(MessagingModel.OutboundMessage msg) {
        MessagingModel.OutboundMessageBody body = msg.getBody();
        MessageBody content = new MessageBody();
        content.text = body.getText();
        content.url = body.getUrl();

        if (body.hasMedia()) {
            content.media = new Media(
                    body.getMedia().getUrl(),
                    Media.MediaType.valueOf(body.getMedia().getMediaValue()));
        }

        if (body.hasLocation()) {
            content.location = new Location(
                    body.getLocation().getLatitude(),
                    body.getLocation().getLongitude(),
                    body.getLocation().getLabel().getValue(),
                    body.getLocation().getAddress().getValue()
            );
        }

        if (body.hasEmail()) {
            MessagingModel.EmailMessageBody email = body.getEmail();
            content.email = new Email(email.getSubject(), email.getBodyPlain(), email.getBodyHtml());
            content.email.cc.addAll(email.getCcListList());
            content.email.bcc.addAll(email.getBccListList());
            content.email.attachments.addAll(email.getAttachmentsList());
        }

        if (body.hasTemplate()) {
            content.template = new Template(body.getTemplate().getId(), body.getTemplate().getParamsMap());
        }

        if (body.hasVoice()) {
      content.voice.addAll(
          body.getVoice().getActionsList().stream()
              .map(
                  action -> {
                    if (action.hasSay()) {
                      MessagingModel.SayCallAction sayAction = action.getSay();
                      return new Say(
                          sayAction.getText(),
                          sayAction.getPlayBeep(),
                          TextToSpeechVoice.valueOf(sayAction.getVoiceValue()));
                    }

                    if (action.hasPlay()) {
                      return new Play(action.getPlay().getUrl());
                    }

                    if (action.hasGetDigits()) {
                      MessagingModel.GetDigitsCallAction getDigitsAction = action.getGetDigits();
                      GetDigits getDigits = null;
                      if (getDigitsAction.hasSay()) {
                        getDigits =
                            new GetDigits(
                                new Say(
                                    getDigitsAction.getSay().getText(),
                                    getDigitsAction.getSay().getPlayBeep(),
                                    TextToSpeechVoice.valueOf(
                                        getDigitsAction.getSay().getVoiceValue())),
                                getDigitsAction.getTimeout().getSeconds(),
                                getDigitsAction.getFinishOnKey().getValue(),
                                getDigitsAction.getNumDigits().getValue());
                      } else if (getDigitsAction.hasPlay()) {
                        getDigits =
                            new GetDigits(
                                new Play(getDigitsAction.getPlay().getUrl()),
                                getDigitsAction.getTimeout().getSeconds(),
                                getDigitsAction.getFinishOnKey().getValue(),
                                getDigitsAction.getNumDigits().getValue());
                      }
                      return getDigits;
                    }

                    if (action.hasDial()) {
                        MessagingModel.DialCallAction dialAction = action.getDial();
                        return new Dial(
                                dialAction.getCustomerNumbersList().stream()
                                        .map(item -> new CustomerNumber(
                                                item.getNumber(),
                                                CustomerNumber.Provider.valueOf(item.getProviderValue())))
                                        .collect(Collectors.toList()),
                                dialAction.getRecord(),
                                dialAction.getSequential(),
                                dialAction.getRingbackTone().getValue(),
                                dialAction.getCallerId().getValue(),
                                dialAction.getMaxDuration().getValue()
                        );
                    }

                    if (action.hasRecordSession()) {
                        return new RecordSession();
                    }

                    if (action.hasGetRecording()) {
                      MessagingModel.GetRecordingCallAction getRecordingAction =
                          action.getGetRecording();
                      GetRecording getRecording = null;
                      if (getRecordingAction.hasSay()) {
                          getRecording = new GetRecording(
                                  new Say(
                                          getRecordingAction.getSay().getText(),
                                          getRecordingAction.getSay().getPlayBeep(),
                                          TextToSpeechVoice.valueOf(
                                                  getRecordingAction.getSay().getVoiceValue())),
                                  getRecordingAction.getTimeout().getSeconds(),
                                  getRecordingAction.getMaxLength().getSeconds(),
                                  getRecordingAction.getFinishOnKey().getValue(),
                                  getRecordingAction.getPlayBeep(),
                                  getRecordingAction.getTrimSilence()
                          );
                      } else if (getRecordingAction.hasPlay()) {
                          getRecording = new GetRecording(
                                  new Play(getRecordingAction.getPlay().getUrl()),
                                  getRecordingAction.getTimeout().getSeconds(),
                                  getRecordingAction.getMaxLength().getSeconds(),
                                  getRecordingAction.getFinishOnKey().getValue(),
                                  getRecordingAction.getPlayBeep(),
                                  getRecordingAction.getTrimSilence()
                          );
                      }
                      return getRecording;
                    }

                    if (action.hasEnqueue()) {
                      return new Enqueue(
                          action.getEnqueue().getQueueName().getValue(),
                          action.getEnqueue().getHoldMusic().getValue());
                    }

                    if (action.hasDequeue()) {
                      return new Dequeue(
                          action.getDequeue().getQueueName().getValue(),
                          new MessagingChannel(
                              action.getDequeue().getChannelNumber().getNumber(),
                              MessagingChannel.Channel.valueOf(
                                  action.getDequeue().getChannelNumber().getChannelValue())),
                          action.getDequeue().getRecord());
                    }

                    if (action.hasReject()) {
                      return new Reject();
                    }

                    if (action.hasRedirect()) {
                      return new Redirect(action.getRedirect().getUrl());
                    }

                    return null;
                  })
              .filter(Objects::nonNull)
              .collect(Collectors.toList()));
        }

        if (body.hasUssd()) {
            content.ussd = new UssdMenu(body.getUssd().getText(), body.getUssd().getIsTerminal());
        }


        Message result = new Message(content);
        result.providerTag = msg.getProviderTag().getValue();
        result.labels = msg.getLabelsList();
        result.replyPrompt = new ReplyTokenPrompt();
        result.replyPrompt.action = PromptMessageReplyAction.valueOf(msg.getReplyPrompt().getActionValue());
        result.replyPrompt.menu = msg.getReplyPrompt().getMenuList().stream().map((item) -> {
            ReplyTokenPromptMenu menu = new ReplyTokenPromptMenu();
            menu.text = item.getText();
            if (item.hasMedia()) {
                menu.media = new Media(
                        item.getMedia().getUrl(),
                        Media.MediaType.valueOf(item.getMedia().getMediaValue()));
            }
            return menu;
        }).collect(Collectors.toList());

        return result;
    }
}
