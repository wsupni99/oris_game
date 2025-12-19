package ru.itis.garticphone.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void messageFieldsShouldBeStoredCorrectly() {
        Message message = new Message(
                MessageType.CHAT,
                1,
                10,
                "Danya",
                "hello"
        );

        assertEquals(MessageType.CHAT, message.getType());
        assertEquals(1, message.getRoomId());
        assertEquals(10, message.getPlayerId());
        assertEquals("Danya", message.getPlayerName());
        assertEquals("hello", message.getPayload());
    }

    @Test
    void settersShouldUpdateFields() {
        Message message = new Message();
        message.setType(MessageType.JOIN);
        message.setRoomId(2);
        message.setPlayerId(20);
        message.setPlayerName("Artur");
        message.setPayload("{\"data\":\"value\"}");

        assertEquals(MessageType.JOIN, message.getType());
        assertEquals(2, message.getRoomId());
        assertEquals(20, message.getPlayerId());
        assertEquals("Artur", message.getPlayerName());
        assertEquals("{\"data\":\"value\"}", message.getPayload());
    }
}
