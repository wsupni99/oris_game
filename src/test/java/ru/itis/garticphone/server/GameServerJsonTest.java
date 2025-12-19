package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GameServerJsonTest {

    @Test
    void toJsonAndParseMessageShouldBeConsistent() throws Exception {
        GameServer server = new GameServer();

        Message original = new Message(
                MessageType.CHAT,
                5,
                42,
                "Tester",
                "hello world"
        );

        Method toJson = GameServer.class.getDeclaredMethod("toJson", Message.class);
        toJson.setAccessible(true);
        String json = (String) toJson.invoke(server, original);

        Method parseMessage = GameServer.class.getDeclaredMethod("parseMessage", String.class);
        parseMessage.setAccessible(true);
        Message parsed = (Message) parseMessage.invoke(server, json);

        assertNotNull(parsed);
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getRoomId(), parsed.getRoomId());
        assertEquals(original.getPlayerId(), parsed.getPlayerId());
        assertEquals(original.getPlayerName(), parsed.getPlayerName());
        assertEquals(original.getPayload(), parsed.getPayload());
    }
}
