package com.elarian;


import com.elarian.hera.proto.ActivityModel;
import com.elarian.hera.proto.ActivityStateOuterClass;
import com.elarian.hera.proto.AppModel;
import com.elarian.hera.proto.AppSocket;
import com.elarian.hera.proto.CommonModel;
import com.elarian.hera.proto.IdentityStateOuterClass;
import com.elarian.hera.proto.MessagingModel;
import com.elarian.hera.proto.PaymentStateOuterClass;
import com.elarian.model.Activity;
import com.elarian.model.ActivityChannel;
import com.elarian.model.ActivityState;
import com.elarian.model.Cash;
import com.elarian.model.ConsentAction;
import com.elarian.model.ConsentUpdateReply;
import com.elarian.model.CustomerNumber;
import com.elarian.model.CustomerState;
import com.elarian.model.CustomerStateUpdateReply;
import com.elarian.model.DataValue;
import com.elarian.model.IdentityState;
import com.elarian.model.Message;
import com.elarian.model.MessageDeliveryStatus;
import com.elarian.model.MessageReply;
import com.elarian.model.MessagingChannel;
import com.elarian.model.MessagingConsentUpdateStatus;
import com.elarian.model.MessagingState;
import com.elarian.model.PaymentState;
import com.elarian.model.Reminder;
import com.elarian.model.SecondaryId;
import com.elarian.model.Tag;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

public final class Customer implements ICustomer {

    private final Function<byte[], AppSocket.AppToServerCommandReply> replyDeserializer = (data) -> {
        try {
            return AppSocket.AppToServerCommandReply.newBuilder().mergeFrom(data).build();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            return null;
        }
    };
    private String customerId;
    protected CustomerNumber customerNumber;
    private final Elarian client;

    public Customer(Elarian client, String customerId) {
        this.client = client;
        this.customerId = customerId;
    }

    public Customer(Elarian client, CustomerNumber customerNumber) {
        this.client = client;
        this.customerNumber = customerNumber;
    }


    /**
     * Get customer id
     * @return
     */
    @Override
    public Mono<String> getCustomerId() {
        if (customerId != null) {
            return Mono.just(customerId);
        }

        return new Mono<String>() {
            @Override
            public void subscribe(CoreSubscriber<? super String> subscriber) {
                getState().subscribe(customerState -> {
                    subscriber.onNext(customerState.customerId);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     *
     * @return
     */
    @Override
    public Mono<CustomerNumber> getCustomerNumber() {
        if (customerNumber != null) {
            return Mono.just(customerNumber);
        }

        return new Mono<CustomerNumber>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerNumber> subscriber) {
                getState().subscribe(state -> {
                    if (!state.paymentState.customerNumbers.isEmpty()) {
                        customerNumber = state.paymentState.customerNumbers.get(0);
                    } else if (!state.activityState.customerNumbers.isEmpty()) {
                        customerNumber = state.activityState.customerNumbers.get(0);
                    } else {
                        List<CustomerNumber> candidates = state.messagingState.channels.stream().map(it -> {
                            if (it.inSession != null) {
                                return it.inSession.customerNumber;
                            }
                            if (it.blocked != null) {
                                return it.blocked.customerNumber;
                            }
                            if (it.active != null) {
                                return it.active.customerNumber;
                            }
                            return null;
                        }).filter(Objects::nonNull).collect(Collectors.toList());
                        if (!candidates.isEmpty()) {
                            customerNumber = candidates.get(0);
                        }
                    }
                    if (customerNumber == null) {
                        subscriber.onError(new RuntimeException("Could not find a number associated with this customer"));
                    } else {
                        subscriber.onNext(customerNumber);
                        subscriber.onComplete();
                    }
                }, subscriber::onError);
            }
        };
    }

    /**
     * Fetch the customer state
     *
     * @return
     */
    @Override
    public Mono<CustomerState> getState() {
        AppSocket.GetCustomerStateCommand.Builder cmd = AppSocket.GetCustomerStateCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setGetCustomerState(cmd)
                .build();

        return new Mono<CustomerState>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerState> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.GetCustomerStateReply res = reply.getGetCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }

                    AppSocket.CustomerStateReplyData data = res.getData();

                    customerId = data.getCustomerId();
                    CustomerState state = new CustomerState(customerId);
                    if (data.hasIdentityState()) {
                        IdentityStateOuterClass.IdentityState identity = data.getIdentityState();
                        state.identityState = new IdentityState();
                        state.identityState.secondaryIds = identity
                                .getSecondaryIdsList()
                                .stream()
                                .map((item) -> new SecondaryId(
                                        item.getMapping().getKey(),
                                        item.getMapping().getValue().getValue()
                                ))
                                .collect(Collectors.toList());
                        state.identityState.tags = identity
                                .getTagsList()
                                .stream()
                                .map((item) -> new Tag(
                                        item.getMapping().getKey(),
                                        item.getMapping().getValue().getValue(),
                                        item.getExpiresAt().getSeconds()))
                                .collect(Collectors.toList());

                        identity.getMetadataMap().forEach((key, value) -> {
                            DataValue val = null;
                            String strVal = value.getStringVal();
                            ByteString byteString = value.getBytesVal();
                            if (byteString != null && !byteString.isEmpty()) {
                                val = DataValue.of(byteString.toByteArray());
                            } else if (strVal != null && !strVal.isEmpty()) {
                                val = DataValue.of(strVal);
                            }
                            state.identityState.metadata.put(key, val);
                        });
                    }

                    if (data.hasActivityState()) {
                        ActivityStateOuterClass.ActivityState activityState = data.getActivityState();
                        state.activityState = new ActivityState();
                        state.activityState.customerNumbers = activityState
                                .getCustomerNumbersList()
                                .stream()
                                .map(Utils::makeCustomerNumber)
                                .collect(Collectors.toList());
                        state.activityState.sessions = activityState
                                .getSessionsList()
                                .stream()
                                .map(item -> {
                                    ActivityState.Session session = new ActivityState.Session();
                                    session.sessionId = item.getSessionId();
                                    session.appId = item.getAppId();
                                    session.createdAt = item.getCreatedAt().getSeconds();
                                    session.updatedAt = item.getUpdatedAt().getSeconds();
                                    session.customerNumber = Utils.makeCustomerNumber(item.getCustomerNumber());
                                    session.channelNumber = Utils.makeActivityChannel(item.getChannelNumber());
                                    session.activities = item.getActivitiesList()
                                            .stream()
                                            .map((act) ->
                                                    new Activity(
                                                            act.getKey(),
                                                            act.getPropertiesMap(),
                                                            item.getSessionId(),
                                                            act.getCreatedAt().getSeconds()))
                                            .collect(Collectors.toList());
                                    return session;
                                })
                                .collect(Collectors.toList());
                    }

                    if (data.hasPaymentState()) {
                        PaymentStateOuterClass.PaymentState paymentState = data.getPaymentState();
                        state.paymentState = new PaymentState();
                        state.paymentState.customerNumbers = paymentState
                                .getCustomerNumbersList()
                                .stream()
                                .map(Utils::makeCustomerNumber)
                                .collect(Collectors.toList());

                        if (customerNumber == null && state.paymentState.customerNumbers.size() > 0) {
                            customerNumber = state.paymentState.customerNumbers.get(0);
                        }

                        state.paymentState.paymentChannels = paymentState
                                .getChannelNumbersList()
                                .stream()
                                .map(Utils::makePaymentChannel)
                                .collect(Collectors.toList());
                        state.paymentState.transactionLog = paymentState
                                .getTransactionLogList()
                                .stream()
                                .map(Utils::makeTransactionLog)
                                .collect(Collectors.toList());
                        state.paymentState.pendingTransactions = paymentState
                                .getPendingTransactionsList()
                                .stream()
                                .map(Utils::makeTransactionLog)
                                .collect(Collectors.toList());

                        paymentState.getWalletsMap().forEach((key, val) -> {
                            PaymentState.PaymentBalance balance = new PaymentState.PaymentBalance();
                            balance.sequenceNr = val.getSequenceNr();
                            balance.currencyCode = val.getCurrencyCode();
                            balance.actual = new Cash(val.getActual().getCurrencyCode(), val.getActual().getAmount());
                            balance.available = new Cash(val.getAvailable().getCurrencyCode(), val.getAvailable().getAmount());
                            val.getPendingMap().forEach((k, v) -> {
                                balance.pending.put(k, new PaymentState.PendingPaymentTransaction(
                                        v.getCreatedAt().getSeconds(),
                                        new Cash(v.getValue().getCurrencyCode(), v.getValue().getAmount()),
                                        new Cash(v.getConverted().getCurrencyCode(), v.getConverted().getAmount())
                                ));
                            });
                            state.paymentState.wallets.put(key, balance);
                        });
                    }

                    if (data.hasMessagingState()) {
                        state.messagingState = new MessagingState();

                        state.messagingState.messages = data.getMessagingState()
                                .getMessagesList()
                                .stream()
                                .map(Utils::makeChannelMessage)
                                .collect(Collectors.toList());

                        state.messagingState.sessions = data.getMessagingState()
                                .getSessionsList()
                                .stream()
                                .map(Utils::makeCompleteMessagingSession)
                                .collect(Collectors.toList());

                        state.messagingState.channels = data.getMessagingState()
                                .getChannelsList()
                                .stream()
                                .map(Utils::makeMessagingChannelState)
                                .collect(Collectors.toList());

                        if (customerNumber == null && state.messagingState.channels.size() > 0) {
                            List<CustomerNumber> numbers = state.messagingState.channels.stream().map(st -> {
                                if (st.active != null) {
                                    return st.active.customerNumber;
                                }

                                if (st.blocked != null) {
                                    return st.blocked.customerNumber;
                                }

                                if (st.inSession != null) {
                                    return st.inSession.customerNumber;
                                }

                                return null;
                            })
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                            if (numbers.size() > 0) {
                                customerNumber = numbers.get(0);
                            }
                        }
                    }

                    subscriber.onNext(state);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Adopt another customer's state
     *
     * @param otherCustomer
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> adoptState(ICustomer otherCustomer) {

        AppSocket.AdoptCustomerStateCommand.Builder cmd = AppSocket.AdoptCustomerStateCommand
                .newBuilder();

        if (customerId == null) {
            throw new RuntimeException("Invalid customer. customerId needs to be set");
        }

        cmd.setCustomerId(customerId);
        Customer target = (Customer) otherCustomer;
        if (target.customerId != null) {
            cmd.setOtherCustomerId(target.customerId);
        } else if (target.customerNumber != null) {
            cmd.setOtherCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(target.customerNumber.number)
                    .setProviderValue(target.customerNumber.provider.getValue())
                    .build());
        }

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setAdoptCustomerState(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Send a message
     *
     * @param channel
     * @param message
     * @return
     */
    @Override
    public Mono<MessageReply> sendMessage(MessagingChannel channel, Message message) {
        AppSocket.SendMessageCommand.Builder cmd = AppSocket.SendMessageCommand
                .newBuilder();

        if (customerNumber == null) {
            throw new RuntimeException("Invalid customer. customerNumber needs to be set");
        }

        cmd.setCustomerNumber(CommonModel.CustomerNumber
                .newBuilder()
                .setNumber(customerNumber.number)
                .setProviderValue(customerNumber.provider.getValue())
                .build());
        cmd.setChannelNumber(MessagingModel.MessagingChannelNumber
                .newBuilder()
                .setNumber(channel.number)
                .setChannelValue(channel.channel.getValue())
                .build());

        cmd.setMessage(Utils.buildOutgoingMessage(message));

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setSendMessage(cmd)
                .build();

        return new Mono<MessageReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super MessageReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.SendMessageReply res = reply.getSendMessage();
                    subscriber.onNext(new MessageReply(
                            res.getMessageId().getValue(),
                            res.getSessionId().getValue(),
                            res.getCustomerId().getValue(),
                            MessageDeliveryStatus.valueOf(res.getStatusValue()),
                            res.getDescription()
                    ));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Reply to a message
     *
     * @param messageId
     * @param message
     * @return
     */
    @Override
    public Mono<MessageReply> replyToMessage(String messageId, Message message) {

        AppSocket.ReplyToMessageCommand.Builder cmd = AppSocket.ReplyToMessageCommand
                .newBuilder()
                .setCustomerId(customerId)
                .setMessageId(messageId);

        cmd.setMessage(Utils.buildOutgoingMessage(message));

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setReplyToMessage(cmd)
                .build();

        return new Mono<MessageReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super MessageReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.SendMessageReply res = reply.getSendMessage();
                    subscriber.onNext(new MessageReply(
                            res.getMessageId().getValue(),
                            res.getSessionId().getValue(),
                            res.getCustomerId().getValue(),
                            MessageDeliveryStatus.valueOf(res.getStatusValue()),
                            res.getDescription()
                    ));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };

    }


    /**
     * Update customer activity
     *
     * @param channel
     * @param activity
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> updateActivity(ActivityChannel channel, Activity activity) {

        if (customerNumber == null) {
            throw new RuntimeException("Invalid customer. customerNumber needs to be set");
        }

        AppSocket.CustomerActivityCommand.Builder cmd = AppSocket.CustomerActivityCommand
                .newBuilder()
                .setSessionId(activity.sessionId)
                .setKey(activity.key)
                .putAllProperties(activity.properties)
                .setChannelNumber(ActivityModel.ActivityChannelNumber
                        .newBuilder()
                        .setNumber(channel.number)
                        .setChannelValue(channel.channel.getValue())
                        .build())
                .setCustomerNumber(
                        CommonModel.CustomerNumber
                                .newBuilder()
                                .setNumber(customerNumber.number)
                                .setProviderValue(customerNumber.provider.getValue())
                                .build()
                );


        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setCustomerActivity(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.CustomerActivityReply res = reply.getCustomerActivity();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };

    }

    /**
     * Update messaging consent for given channel
     * @param channel
     * @param action
     * @return
     */
    @Override
    public Mono<ConsentUpdateReply> updateMessagingConsent(MessagingChannel channel, ConsentAction action) {

        if (customerNumber == null) {
            throw new RuntimeException("Invalid customer. customerNumber needs to be set");
        }

        AppSocket.UpdateMessagingConsentCommand.Builder cmd = AppSocket.UpdateMessagingConsentCommand
                .newBuilder()
                .setChannelNumber(MessagingModel.MessagingChannelNumber
                        .newBuilder()
                        .setNumber(channel.number)
                        .setChannelValue(channel.channel.getValue())
                        .build())
                .setCustomerNumber(
                        CommonModel.CustomerNumber
                                .newBuilder()
                                .setNumber(customerNumber.number)
                                .setProviderValue(customerNumber.provider.getValue())
                                .build()
                )
                .setUpdateValue(action.getValue());


        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setUpdateMessagingConsent(cmd)
                .build();

        return new Mono<ConsentUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super ConsentUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateMessagingConsentReply res = reply.getUpdateMessagingConsent();
                    subscriber.onNext(new ConsentUpdateReply(
                            MessagingConsentUpdateStatus.valueOf(res.getStatusValue()),
                            res.getDescription(),
                            res.getCustomerId().getValue()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Lease app data
     *
     * @return
     */
    @Override
    public Mono<DataValue> leaseAppData() {

        AppSocket.LeaseCustomerAppDataCommand.Builder cmd = AppSocket.LeaseCustomerAppDataCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }


        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setLeaseCustomerAppData(cmd)
                .build();

        return new Mono<DataValue>() {
            @Override
            public void subscribe(CoreSubscriber<? super DataValue> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.LeaseCustomerAppDataReply res = reply.getLeaseCustomerAppData();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }

                    DataValue appData = null;
                    if (res.hasValue()) {
                        String strVal = res.getValue().getStringVal();
                        ByteString byteString = res.getValue().getBytesVal();
                        if (byteString != null && !byteString.isEmpty()) {
                            appData = DataValue.of(byteString.toByteArray());
                        } else if (strVal != null && !strVal.isEmpty()) {
                            appData = DataValue.of(strVal);
                        }
                    }

                    subscriber.onNext(appData);
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };

    }

    /**
     * Update customer app data
     *
     * @param data
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> updateAppData(DataValue data) {

        AppSocket.UpdateCustomerAppDataCommand.Builder cmd = AppSocket.UpdateCustomerAppDataCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        CommonModel.DataMapValue.Builder update = CommonModel.DataMapValue.newBuilder();
        if (data.bytes != null) {
            update.setBytesVal(ByteString.copyFrom(data.bytes));
        } else if (data.string != null) {
            update.setStringVal(data.string);
        }
        cmd.setUpdate(update);

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setUpdateCustomerAppData(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerAppDataReply res = reply.getUpdateCustomerAppData();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Delete customer app data
     *
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> deleteAppData() {

        AppSocket.DeleteCustomerAppDataCommand.Builder cmd = AppSocket.DeleteCustomerAppDataCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setDeleteCustomerAppData(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerAppDataReply res = reply.getUpdateCustomerAppData();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Get customer's metadata
     * @return
     */
    @Override
    public Mono<Map<String, DataValue>> getMetadata() {
        return new Mono<Map<String, DataValue>>() {
          @Override
          public void subscribe(CoreSubscriber<? super Map<String, DataValue>> subscriber) {
            getState()
                .subscribe(
                    state -> {
                      if (state.identityState != null) {
                        subscriber.onNext(state.identityState.metadata);
                      } else {
                          subscriber.onNext(new HashMap<>());
                      }
                      subscriber.onComplete();
                    },
                    subscriber::onError);
              }
        };
    }


    /**
     * Update customer metadata
     *
     * @param metadata
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> updateMetadata(Map<String, DataValue> metadata) {

        AppSocket.UpdateCustomerMetadataCommand.Builder cmd = AppSocket.UpdateCustomerMetadataCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        Map<String, CommonModel.DataMapValue> updates = new HashMap<>();
        metadata.forEach((key, value) -> {
            CommonModel.DataMapValue.Builder update = CommonModel.DataMapValue.newBuilder();
            if (value.bytes != null) {
                update.setBytesVal(ByteString.copyFrom(value.bytes));
            } else if (value.string != null) {
                update.setStringVal(value.string);
            }
            updates.put(key, update.build());
        });

        cmd.putAllUpdates(updates);

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setUpdateCustomerMetadata(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Delete customer metadata
     *
     * @param keys
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> deleteMetadata(List<String> keys) {

        AppSocket.DeleteCustomerMetadataCommand.Builder cmd = AppSocket.DeleteCustomerMetadataCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        cmd.addAllDeletions(keys);

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setDeleteCustomerMetadata(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Get customer's secondary Ids
     * @return
     */
    public Mono<List<SecondaryId>> getSecondaryIds() {
        return new Mono<List<SecondaryId>>() {
            @Override
            public void subscribe(CoreSubscriber<? super List<SecondaryId>> subscriber) {
                getState()
                        .subscribe(
                                state -> {
                                    if (state.identityState != null) {
                                        subscriber.onNext(state.identityState.secondaryIds);
                                    } else {
                                        subscriber.onNext(new ArrayList<>());
                                    }
                                    subscriber.onComplete();
                                },
                                subscriber::onError);
            }
        };
    }

    /**
     * Update secondary Ids
     *
     * @param secondaryIds
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> updateSecondaryIds(List<SecondaryId> secondaryIds) {

        AppSocket.UpdateCustomerSecondaryIdCommand.Builder cmd = AppSocket.UpdateCustomerSecondaryIdCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        cmd.addAllUpdates(secondaryIds.stream().map((secondaryId) -> CommonModel.CustomerIndex
                .newBuilder()
                .setMapping(CommonModel.IndexMapping
                        .newBuilder()
                        .setKey(secondaryId.key)
                        .setValue(StringValue.of(secondaryId.value))
                        .build())
                .build())
                .collect(Collectors.toList()));

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setUpdateCustomerSecondaryId(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     * Delete secondary ids
     *
     * @param secondaryIds
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> deleteSecondaryIds(List<SecondaryId> secondaryIds) {

        AppSocket.DeleteCustomerSecondaryIdCommand.Builder cmd = AppSocket.DeleteCustomerSecondaryIdCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        cmd.addAllDeletions(secondaryIds.stream().map((secondaryId) -> CommonModel.IndexMapping
                .newBuilder()
                .setKey(secondaryId.key)
                .setValue(StringValue.of(secondaryId.value))
                .build())
                .collect(Collectors.toList()));

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setDeleteCustomerSecondaryId(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Get customer's tags
     * @return
     */
    @Override
    public Mono<List<Tag>> getTags() {
        return new Mono<List<Tag>>() {
            @Override
            public void subscribe(CoreSubscriber<? super List<Tag>> subscriber) {
                getState()
                        .subscribe(
                                state -> {
                                    if (state.identityState != null) {
                                        subscriber.onNext(state.identityState.tags);
                                    } else {
                                        subscriber.onNext(new ArrayList<>());
                                    }
                                    subscriber.onComplete();
                                },
                                subscriber::onError);
            }
        };
    }

    /**
     * Update customer tags
     *
     * @param tags
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> updateTags(List<Tag> tags) {

        AppSocket.UpdateCustomerTagCommand.Builder cmd = AppSocket.UpdateCustomerTagCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        tags.forEach(tag -> {
            CommonModel.CustomerIndex.Builder idx = CommonModel.CustomerIndex
                    .newBuilder()
                    .setMapping(CommonModel.IndexMapping
                            .newBuilder()
                            .setKey(tag.key)
                            .setValue(StringValue.of(tag.value))
                            .build());
            if (tag.expiresAt > 0) {
                idx.setExpiresAt(Timestamp.newBuilder().setSeconds(tag.expiresAt).build());
            }
            cmd.addUpdates(idx);
        });

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setUpdateCustomerTag(cmd.build())
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * @param keys
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> deleteTags(List<String> keys) {

        AppSocket.DeleteCustomerTagCommand.Builder cmd = AppSocket.DeleteCustomerTagCommand
                .newBuilder();

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        cmd.addAllDeletions(keys);

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setDeleteCustomerTag(cmd)
                .build();

        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerStateReply res = reply.getUpdateCustomerState();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }

    /**
     * Add a customer reminder
     *
     * @param reminder
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> addReminder(Reminder reminder) {
        AppModel.CustomerReminder.Builder rem = AppModel.CustomerReminder
                .newBuilder()
                .setKey(reminder.key)
                .setRemindAt(Timestamp
                        .newBuilder()
                        .setSeconds(reminder.remindAt)
                        .build());

        if (reminder.payload != null) {
            rem.setPayload(StringValue.of(reminder.payload));
        }

        if (reminder.interval >= 60) {
            rem.setInterval(Duration
                    .newBuilder()
                    .setSeconds(reminder.interval)
                    .build());
        }

        AppSocket.AddCustomerReminderCommand.Builder cmd = AppSocket.AddCustomerReminderCommand
                .newBuilder()
                .setReminder(rem);

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setAddCustomerReminder(cmd)
                .build();


        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerAppDataReply res = reply.getUpdateCustomerAppData();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }


    /**
     * Cancel a customer reminder
     *
     * @param key
     * @return
     */
    @Override
    public Mono<CustomerStateUpdateReply> cancelReminder(String key) {

        AppSocket.CancelCustomerReminderCommand.Builder cmd = AppSocket.CancelCustomerReminderCommand
                .newBuilder()
                .setKey(key);

        if (customerNumber != null) {
            cmd.setCustomerNumber(CommonModel.CustomerNumber
                    .newBuilder()
                    .setNumber(customerNumber.number)
                    .setProviderValue(customerNumber.provider.getValue())
                    .build());
        } else if (customerId != null) {
            cmd.setCustomerId(customerId);
        } else {
            throw new RuntimeException("Invalid customer. customerId and/or customerNumber need to be set");
        }

        AppSocket.AppToServerCommand req = AppSocket.AppToServerCommand
                .newBuilder()
                .setCancelCustomerReminder(cmd)
                .build();


        return new Mono<CustomerStateUpdateReply>() {
            @Override
            public void subscribe(CoreSubscriber<? super CustomerStateUpdateReply> subscriber) {
                client.buildCommandReply(req.toByteArray(), replyDeserializer).subscribe(reply -> {
                    AppSocket.UpdateCustomerAppDataReply res = reply.getUpdateCustomerAppData();
                    if (!res.getStatus()) {
                        subscriber.onError(new RuntimeException(res.getDescription()));
                        return;
                    }
                    subscriber.onNext(new CustomerStateUpdateReply(
                            true,
                            res.getCustomerId().getValue(),
                            res.getDescription()));
                    subscriber.onComplete();
                }, subscriber::onError);
            }
        };
    }
}
