package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;
import static org.junit.jupiter.api.Assertions.*;

class GameServerJsonTest {

    @Test
    void toJsonAndParseMessageShouldBeConsistent() {
        Message original = new Message(
                MessageType.CHAT,
                5,
                42,
                "Tester",
                "hello world"
        );

        String json = Message.toJson(original);
        Message parsed = Message.parse(json);

        assertNotNull(parsed);
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getRoomId(), parsed.getRoomId());
        assertEquals(original.getPlayerId(), parsed.getPlayerId());
        assertEquals(original.getPlayerName(), parsed.getPlayerName());
        assertEquals(original.getPayload(), parsed.getPayload());
    }

    @Test
    void parseMessageShouldReturnNullOnInvalidJson() {
        assertNull(Message.parse("not a json"));
        assertNull(Message.parse(""));
        assertNull(Message.parse(null));
    }

    @Test
    void toJsonShouldEscapeQuotesAndBackslashes() {
        String trickyName = "Da\"nya\\Test";
        String trickyPayload = "{ \"k\":\"v\\\\\" }";

        Message original = new Message(
                MessageType.CHAT,
                1,
                10,
                trickyName,
                trickyPayload
        );

        String json = Message.toJson(original);
        Message parsed = Message.parse(json);

        assertEquals(trickyName, parsed.getPlayerName());
        assertEquals(trickyPayload, parsed.getPayload());
    }
}
