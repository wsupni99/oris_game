package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.TestPlayer;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
class GameServerModesTest {

    @Test
    @SuppressWarnings("unchecked")
    void endRoundShouldSendWordInGuessMode() throws Exception {
        ScheduledExecutorService roundScheduler = Executors.newScheduledThreadPool(1);
        GameService service = new GameService(roundScheduler);

        Field roomsField = GameService.class.getDeclaredField("rooms");
        roomsField.setAccessible(true);
        Map<Integer, GameState> rooms = (Map<Integer, GameState>) roomsField.get(service);

        Field secretWordsField = GameService.class.getDeclaredField("secretWords");
        secretWordsField.setAccessible(true);
        Map<Integer, String> secretWords = (Map<Integer, String>) secretWordsField.get(service);

        GameState room = new GameState(1, GameMode.GUESS_DRAWING);
        TestPlayer p1 = new TestPlayer(1, "P1");
        room.addPlayer(p1);
        rooms.put(1, room);
        secretWords.put(1, "кот");

        Method endRound = GameService.class.getDeclaredMethod("endRound", int.class);
        endRound.setAccessible(true);
        endRound.invoke(service, 1);

        assertEquals(1, p1.getSent().size());
        Message msg = p1.getSent().get(0);
        assertEquals(MessageType.ROUND_UPDATE, msg.getType());
        assertTrue(msg.getPayload().contains("кот"));
    }
}
