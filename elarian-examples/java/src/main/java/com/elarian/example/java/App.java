package com.elarian.example.java;

import com.elarian.*;
import com.elarian.model.*;

import java.util.*;
import java.util.logging.Logger;
import reactor.core.publisher.Mono;

public class App {

  static Logger log = Logger.getLogger(App.class.getName());
  static Elarian app;
  static PaymentChannel mpesaChannel;
  static MessagingChannel smsChannel, voiceChannel;

  @SuppressWarnings("DefaultLocale")
  static void approveLoan(Customer customer, String name, float balance) {
    log.info("Approving loan of " + balance + " for " + name);

    long repaymentDate = System.currentTimeMillis() + 60000;

    CustomerNumber number = customer.getCustomerNumber().block();

    app.initiatePayment(
            new PaymentPurseCounterParty(System.getenv("PURSE_ID")),
            new PaymentCustomerCounterParty(number, mpesaChannel),
            new Cash("KES", balance), PaymentMode.HOSTED)
        .flatMap(
            res -> {
              if (res.status != PaymentStatus.SUCCESS
                  && res.status != PaymentStatus.QUEUED
                  && res.status != PaymentStatus.PENDING_CONFIRMATION) {
                throw new RuntimeException("Failed to send loan money: " + res.description);
              }
              Map<String, DataValue> meta = new HashMap<>();
              meta.put("balance", DataValue.of(String.valueOf(balance)));
              return Mono.just(meta);
            })
        .flatMap(customer::updateMetadata)
        .flatMap(
            it -> {
              String text =
                  String.format(
                      "Congratulations %s!\n"
                          + "Your loan of KES %.2f has been approved!\n"
                          + "You are expected to pay it back by %s",
                      name, balance, new Date(repaymentDate));
              Message message = new Message(new MessageBody(text));
              return customer.sendMessage(smsChannel, message);
            })
        .flatMap(it -> customer.addReminder(new Reminder("moni", "", repaymentDate / 1000)))
        .subscribe(
            re -> log.info("Successfully approving loan of " + balance + " for " + name),
            err -> log.warning("Failed approve loan: " + err.getMessage()));
  }

  @SuppressWarnings("DefaultLocale")
  static NotificationHandler<ReminderNotification> reminderHandler =
      (notification, customer, appData, responder) -> {
        log.info("Processing Reminder");
        customer
            .getMetadata()
            .flatMap(
                meta -> {
                  String name = "Unknown customer";
                  float balance = 0;
                  int strike = 1;

                  if (meta.containsKey("name")) {
                    name = meta.get("name").string;
                  }

                  if (meta.containsKey("balance")) {
                    balance = Float.parseFloat(meta.get("balance").string);
                  }

                  if (meta.containsKey("strike")) {
                    strike = Integer.parseInt(meta.get("strike").string);
                  }

                  MessageBody body;
                  MessagingChannel channel = smsChannel;
                  if (strike == 1) {
                    body =
                        new MessageBody(
                            String.format(
                                "Hello %s, this is a friendly reminder to pay back my KES %.2f",
                                name, balance));
                  } else if (strike == 2) {
                    body =
                        new MessageBody(
                            String.format(
                                "Hey %s, you still need to pay back my KES %.2f", name, balance));
                  } else {
                    channel = voiceChannel;
                    body = new MessageBody();
                    body.voice =
                        Collections.singletonList(
                            new Say(
                                String.format(
                                    "Yo %s!!!! you need to pay back my KES %.2f", name, balance),
                                true,
                                TextToSpeechVoice.MALE));
                  }
                  int finalStrike = strike;
                  return customer
                      .sendMessage(channel, new Message(body))
                      .flatMap(
                          it -> {
                            meta.put("strike", DataValue.of(String.valueOf(finalStrike + 1)));
                            return Mono.just(meta);
                          });
                })
            .flatMap(customer::updateMetadata)
            .flatMap(
                it ->
                    customer.addReminder(
                        new Reminder("moni", "", (System.currentTimeMillis() + 60000) / 1000)))
            .subscribe(
                ok -> log.info("Successfully Processed Reminder"),
                err -> log.warning("Failed process reminder: " + err.getMessage()));
      };

  static BaseNotificationHandler<UssdSessionNotification, UssdMenu> ussdSessionHandler =
      (notification, customer, appData, responder) -> {
        try {
          String input = notification.input.text;
          log.info("Processing USSD from " + notification.customerNumber.number);

          customer
              .getMetadata()
              .flatMap(
                  meta -> {
                    String name = null;
                    float balance = 0;
                    if (meta.containsKey("name")) {
                      name = meta.get("name").string;
                    }
                    if (meta.containsKey("balance")) {
                      balance = Float.parseFloat(meta.get("balance").string);
                    }

                    String screen = "home";
                    if (appData != null && appData.string != null) {
                      screen = appData.string;
                    }

                    String nextScreen = screen;
                    if (screen.contentEquals("home") && !input.contentEquals("")) {
                      if (input.contentEquals("1")) {
                        nextScreen = "request-name";
                      } else if (input.contentEquals("2")) {
                        nextScreen = "quit";
                      }
                    }

                    if (screen.contentEquals("home") && input.contentEquals("")) {
                      if (name != null) {
                        nextScreen = "info";
                      }
                    }

                    String text;
                    switch (nextScreen) {
                      case "quit":
                        responder.callback(
                            new UssdMenu("Happy Coding!", true), DataValue.of("home"));
                        break;
                      case "info":
                        text = String.format("Hey %s, ", name);
                        text +=
                            balance > 0
                                ? String.format("you still owe me KES %.2f!", balance)
                                : "you have repaid your loan, good for you!";
                        responder.callback(new UssdMenu(text, true), DataValue.of("home"));
                        break;
                      case "request-name":
                        text = "Alright, what is your name?";
                        responder.callback(
                            new UssdMenu(text, false), DataValue.of("request-amount"));
                        break;
                      case "request-amount":
                        name = input;
                        text = String.format("Okay %s, how much do you need?", name);
                        responder.callback(
                            new UssdMenu(text, false), DataValue.of("approve-amount"));
                        break;
                      case "approve-amount":
                        balance = Float.parseFloat(input);
                        text =
                            String.format(
                                "Awesome! %s we are reviewing your application and will be in touch shortly!\nHave a lovely day!",
                                name);
                        responder.callback(new UssdMenu(text, true), DataValue.of("home"));
                        approveLoan((Customer) customer, name, balance);
                        break;
                      case "home":
                      default:
                        text = "Welcome to MoniMoni!\n1. Apply for loan\n2. Quit";
                        responder.callback(new UssdMenu(text, false), DataValue.of("home"));
                        break;
                    }

                    if (name != null) {
                      meta.put("name", DataValue.of(name));
                    }
                    meta.put("balance", DataValue.of(String.valueOf(balance)));
                    return Mono.just(meta);
                  })
              .flatMap(customer::updateMetadata)
              .subscribe(
                  re ->
                      log.info(
                          "Successfully Processed USSD from " + notification.customerNumber.number),
                  err -> {
                    responder.callback(new UssdMenu("Ooops! Try again later", true), appData);
                    log.warning("Failed process ussd: " + err.getMessage());
                  });
        } catch (Exception ex) {
          log.warning(ex.getMessage());
        }
      };

  @SuppressWarnings("DefaultLocale")
  static NotificationHandler<ReceivedPaymentNotification> paymentReceivedHandler =
      (notification, customer, appData, responder) -> {
        log.info("Processing payment from " + notification.customerNumber.number);
        customer
            .getMetadata()
            .flatMap(
                meta -> {
                  Cash value = notification.value;
                  double newBalance = 0;
                  String name = "Unknown Customer";
                  if (meta.containsKey("balance")) {
                    newBalance = Float.parseFloat(meta.get("balance").string) - value.amount;
                  }
                  if (meta.containsKey("name")) {
                    name = meta.get("name").string;
                  }
                  if (newBalance <= 0) {
                    String finalName = name;
                    return customer
                        .cancelReminder("moni")
                        .flatMap(
                            i ->
                                customer.deleteMetadata(Arrays.asList("name", "strike", "balance")))
                        .flatMap(i -> customer.deleteAppData())
                        .flatMap(
                            i -> {
                              String text =
                                  String.format(
                                      "Thank you for your payment %s, your loan has been fully repaid!!",
                                      finalName);
                              Message message = new Message(new MessageBody(text));
                              return customer.sendMessage(smsChannel, message);
                            });
                  } else {
                    String text =
                        String.format(
                            "Hey %s!\nThank you for your payment, but you still owe me KES %.2f",
                            name, newBalance);
                    Message message = new Message(new MessageBody(text));
                    double balance = newBalance;
                    return customer
                        .sendMessage(smsChannel, message)
                        .flatMap(
                            i -> {
                              meta.put("balance", DataValue.of(String.valueOf(balance)));
                              return customer.updateMetadata(meta);
                            });
                  }
                })
            .subscribe(
                re ->
                    log.info(
                        "Successfully Processed payment from "
                            + notification.customerNumber.number),
                err -> {
                  log.warning("Failed process payment: " + err.getMessage());
                });
      };

  static {
    String appId = System.getenv("APP_ID");
    String orgId = System.getenv("ORG_ID");
    String apiKey = System.getenv("API_KEY");

    app = new Elarian(apiKey, orgId, appId);
    smsChannel =
        new MessagingChannel(System.getenv("SMS_SHORT_CODE"), MessagingChannel.Channel.SMS);
    voiceChannel =
        new MessagingChannel(System.getenv("VOICE_NUMBER"), MessagingChannel.Channel.VOICE);
    mpesaChannel =
        new PaymentChannel(System.getenv("MPESA_PAYBILL"), PaymentChannel.Channel.CELLULAR);

    app.setOnReminderNotificationHandler(reminderHandler);
    app.setOnUssdSessionNotificationHandler(ussdSessionHandler);
    app.setOnReceivedPaymentNotificationHandler(paymentReceivedHandler);
  }

  public static void main(String[] args) {
    log.info("Starting...");

    app.connect(
        new ConnectionListener() {
          @Override
          public void onPending() {
            log.info("Pending...");
          }

          @Override
          public void onConnecting() {
            log.info("Connecting...");
          }

          @Override
          public void onClosed() {
            log.warning("Connection Closed");
          }

          @Override
          public void onError(Throwable throwable) {
            log.severe("Failed to connect: " + throwable.getMessage());
          }

          @Override
          public void onConnected() {
              String ussCode = System.getenv("USSD_CODE");
              log.info("App is connected, waiting for customers on " + ussCode);
          }
        });

    try {
      Thread.currentThread().join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
