package com.elarian.test;

import com.elarian.Elarian;
import com.elarian.model.AuthToken;
import com.elarian.model.Cash;
import com.elarian.model.CustomerNumber;
import com.elarian.model.InitiatePaymentReply;
import com.elarian.model.Message;
import com.elarian.model.MessageBody;
import com.elarian.model.PaymentCustomerCounterParty;
import com.elarian.model.PaymentPurseCounterParty;
import com.elarian.model.Reminder;
import com.elarian.model.Tag;
import com.elarian.model.TagUpdateReply;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
public class ElarianTests {

    private static final Elarian client = new Elarian(Fixtures.API_KEY, Fixtures.ORG_ID, Fixtures.APP_ID);

    @Test
    @Order(1)
    void connect() {
        client.connect(Fixtures.connectionListener);
        await().until(client::isConnected);
    }

    @Test
    @Order(2)
    void generateAuthToken() {
        AuthToken authToken = client.generateAuthToken().block(Duration.ofSeconds(5));
        assertNotNull(authToken);
        assertNotNull(authToken.token);
        assertTrue(authToken.lifetime > 0);
    }

    @Test
    @Order(3)
    void addCustomerReminderByTag() {
        Tag tag = new Tag("test", "value");
        Reminder reminder = new Reminder("rem", "remind-in-a-minute", (System.currentTimeMillis() + 60000) / 1000);
        TagUpdateReply reply = client.addCustomerReminderByTag(tag, reminder).block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(4)
    void cancelCustomerReminderByTag() {
        Tag tag = new Tag("test", "value");
        TagUpdateReply reply = client.cancelCustomerReminderByTag(tag, "rem").block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(5)
    void sendMessageByTag() {
        Tag tag = new Tag("test", "value");
        MessageBody body = new MessageBody("Hello Hello");
        Message message = new Message(body);

        TagUpdateReply reply = client.sendMessageByTag(tag, Fixtures.smsChannel, message).block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(6)
    void initiatePayment() {
        CustomerNumber customerNumber = new CustomerNumber("+254718769000", CustomerNumber.Provider.CELLULAR);
    InitiatePaymentReply reply =
        client
            .initiatePayment(
                new PaymentCustomerCounterParty(customerNumber, Fixtures.mpesaChannel),
                new PaymentPurseCounterParty(Fixtures.PURSE_ID),
                new Cash("KES", 100))
            .block(Duration.ofSeconds(5));

        assertNotNull(reply);
        assertNotNull(reply.debitCustomerId);
    }

    @Test
    @Order(7)
    void disconnect() {
        client.disconnect("Done Test!");
        await().until(() -> !client.isConnected());
    }
}
