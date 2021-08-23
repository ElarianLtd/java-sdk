package com.elarian.test;

import com.elarian.Simulator;
import com.elarian.model.Cash;
import com.elarian.model.Media;
import com.elarian.model.PaymentStatus;
import com.elarian.model.SimulatorMessageBody;
import com.elarian.model.SimulatorReply;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SimulatorTest {

    private static final Simulator client = new Simulator(Fixtures.API_KEY, Fixtures.ORG_ID, Fixtures.APP_ID);

    @Test
    @Order(1)
    void connect() {
        client.connect(Fixtures.connectionListener);
        await().until(client::isConnected);
    }

    @Test
    @Order(2)
    void receiveMessage() {
        List<SimulatorMessageBody> parts = new ArrayList<>();
        parts.add(new SimulatorMessageBody("Hello"));
        parts.add(new SimulatorMessageBody(new Media("https://some-url/img.png", Media.MediaType.IMAGE)));
        SimulatorReply reply = client.receiveMessage("+254718769000", Fixtures.telegramChannel, "some-session-id", parts).block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(3)
    void receivePayment() {
        SimulatorReply reply = client.receivePayment(
                "some-txn-id",
                Fixtures.mpesaChannel,
                "+254718769000",
                new Cash("KES", 100),
                PaymentStatus.PENDING_CONFIRMATION)
                .block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(4)
    void updatePaymentStatus() {
        SimulatorReply reply = client.updatePaymentStatus(
                "some-txn-id",
                PaymentStatus.SUCCESS)
                .block(Duration.ofSeconds(5));
        assertNotNull(reply);
        assertTrue(reply.status);
    }

    @Test
    @Order(5)
    void disconnect() {
        client.disconnect("Done Test!");
        await().until(() -> !client.isConnected());
    }
}
