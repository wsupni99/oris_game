package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class GameServerChatTest {

    private static class TestPlayer extends Player {
        private final List<Message> sent = new ArrayList<>();

        public TestPlayer(int id, String name) {
            super(id, name);
        }

        @Override
        public void send(Message message) {
            sent.add(message);
        }

        public List<Message> getSent() {
            return sent;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void chatShouldBroadcastWithinRoom() throws Exception {
        ScheduledExecutorService roundScheduler = Executors.newScheduledThreadPool(1);
        GameService service = new GameService(roundScheduler);

        Field roomsField = GameService.class.getDeclaredField("rooms");
        roomsField.setAccessible(true);
        Map<Integer, GameState> rooms = (Map<Integer, GameState>) roomsField.get(service);

        GameState room = new GameState(1, GameMode.GUESS_DRAWING);
        TestPlayer p1 = new TestPlayer(1, "P1");
        TestPlayer p2 = new TestPlayer(2, "P2");
        TestPlayer p3 = new TestPlayer(3, "P3");
        room.addPlayer(p1);
        room.addPlayer(p2);
        room.addPlayer(p3);
        rooms.put(1, room);

        Message chat = new Message(
                MessageType.CHAT,
                1,
                p1.getId(),
                p1.getName(),
                "hello"
        );

        Method handleChat = GameService.class
                .getDeclaredMethod("handleChat", Player.class, Message.class);
        handleChat.setAccessible(true);
        handleChat.invoke(service, p1, chat);

        assertEquals(1, p1.getSent().size());
        assertEquals(1, p2.getSent().size());
        assertEquals(1, p3.getSent().size());

        Message msg1 = p1.getSent().get(0);
        assertEquals(MessageType.CHAT, msg1.getType());
        assertEquals(1, msg1.getRoomId());
        assertEquals("P1", msg1.getPlayerName());
        assertEquals("hello", msg1.getPayload());
    }
}
