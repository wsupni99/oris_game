package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GameServerChatTest {

    private static class TestPlayer extends Player {
        private final List<String> sent = new ArrayList<>();

        public TestPlayer(int id, String name) {
            super(id, name);
        }

        @Override
        public void sendLine(String line) {
            sent.add(line);
        }

        public List<String> getSent() {
            return sent;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatShouldBroadcastWithinRoom() throws Exception {
        GameServer server = new GameServer();

        GameState room = new GameState(1, GameMode.GUESS_DRAW);
        TestPlayer p1 = new TestPlayer(1, "P1");
        TestPlayer p2 = new TestPlayer(2, "P2");
        room.addPlayer(p1);
        room.addPlayer(p2);

        Field roomsField = GameServer.class.getDeclaredField("rooms");
        roomsField.setAccessible(true);
        Map<Integer, GameState> rooms =
                (Map<Integer, GameState>) roomsField.get(server);
        rooms.put(1, room);

        Message msg = new Message(
                MessageType.CHAT,
                1,
                1,
                "P1",
                "hi"
        );

        Method handleChat = GameServer.class.getDeclaredMethod(
                "handleChat",
                ru.itis.garticphone.client.Player.class,
                Message.class
        );
        handleChat.setAccessible(true);
        handleChat.invoke(server, p1, msg);

        assertEquals(1, p1.getSent().size());
        assertEquals(1, p2.getSent().size());
        assertTrue(p1.getSent().get(0).contains("\"payload\":\"hi\""));
        assertTrue(p2.getSent().get(0).contains("\"payload\":\"hi\""));
    }
}
