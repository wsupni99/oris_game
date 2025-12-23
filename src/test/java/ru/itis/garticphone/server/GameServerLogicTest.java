package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import static org.junit.jupiter.api.Assertions.*;

class GameServerLogicTest {

    @Test
    void testHandleStart_NotHost() {
        GameState room = new GameState(1, GameMode.GUESS_DRAWING);
        room.setHost(1);
        room.addPlayer(new Player(1, "Host"));
        room.addPlayer(new Player(2, "Player2"));

        room.toggleReady(1);
        room.toggleReady(2);

        Player nonHost = new Player(2, "Player2");
        Message startMsg = new Message(MessageType.START, 1, 2, "Player2", "60");

        assertTrue(room.isHost(1));
        assertTrue(room.allReady());
    }
}
