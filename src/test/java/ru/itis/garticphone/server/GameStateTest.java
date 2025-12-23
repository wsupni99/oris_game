package ru.itis.garticphone.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {
    private GameState guessState;
    private GameState deafState;
    private Player host;
    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        guessState = new GameState(1, GameMode.GUESS_DRAWING);
        deafState = new GameState(2, GameMode.DEAF_PHONE);

        host = new Player(1, "Host");
        player1 = new Player(2, "Player1");
        player2 = new Player(3, "Player2");
    }

    @Test
    void testHostAssignment_FirstPlayer() {
        guessState.setHost(1);
        assertTrue(guessState.isHost(1));
        assertFalse(guessState.isHost(2));
    }

    @Test
    void testAllReady_Guesdraw_Min2_NotReady() {
        guessState.addPlayer(host);
        assertFalse(guessState.allReady()); // 1 < 2
    }

    @Test
    void testAllReady_Guesdraw_2Players_NotReady() {
        guessState.addPlayer(host);
        guessState.addPlayer(player1);
        assertFalse(guessState.allReady()); // 2 игроков, 0 готовых
    }

    @Test
    void testAllReady_Guesdraw_2Players_Ready() {
        guessState.addPlayer(host);
        guessState.addPlayer(player1);
        guessState.toggleReady(1);
        guessState.toggleReady(2);
        assertTrue(guessState.allReady()); // ✅ 2/2 готовы
    }

    @Test
    void testAllReady_Deafphone_Min4_NotEnoughPlayers() {
        deafState.addPlayer(host);
        deafState.addPlayer(player1);
        deafState.addPlayer(player2);
        deafState.toggleReady(1);
        deafState.toggleReady(2);
        deafState.toggleReady(3);
        assertFalse(deafState.allReady()); // 3 < 4 игроков
    }

    @Test
    void testToggleReady_AddRemove() {
        guessState.toggleReady(1);
        assertTrue(guessState.getReadyPlayers().contains(1));

        guessState.toggleReady(1);
        assertFalse(guessState.getReadyPlayers().contains(1));
    }

    @Test
    void testMinPlayers_Guesdraw() {
        assertEquals(2, guessState.getMode() == GameMode.GUESS_DRAWING ? 2 : 4);
    }

    @Test
    void testMinPlayers_Deafphone() {
        assertEquals(4, deafState.getMode() == GameMode.DEAF_PHONE ? 4 : 2);
    }

    @Test
    void shouldAddAndRemovePlayers() {
        GameState gameState = new GameState(1, GameMode.GUESS_DRAWING);

        Player player1 = new Player(1, "P1");
        Player player2 = new Player(2, "P2");

        gameState.addPlayer(player1);
        gameState.addPlayer(player2);

        assertEquals(2, gameState.getPlayers().size());
        assertTrue(gameState.getPlayers().contains(player1));
        assertTrue(gameState.getPlayers().contains(player2));

        gameState.removePlayer(player1);
        assertEquals(1, gameState.getPlayers().size());
        assertFalse(gameState.getPlayers().contains(player1));
    }

    @Test
    void shouldIncreaseRoundAndReset() {
        GameState gameState = new GameState(1, GameMode.DEAF_PHONE);

        assertEquals(1, gameState.getRound());
        gameState.nextRound();
        gameState.nextRound();
        assertEquals(3, gameState.getRound());

        gameState.resetRound();
        assertEquals(1, gameState.getRound());
    }

    @Test
    void timerShouldDecreaseButNotBelowZero() {
        GameState gameState = new GameState(1, GameMode.GUESS_DRAWING);

        gameState.setTimerSeconds(2);
        gameState.decrementTimer();
        assertEquals(1, gameState.getTimerSeconds());
        gameState.decrementTimer();
        assertEquals(0, gameState.getTimerSeconds());
        gameState.decrementTimer();
        assertEquals(0, gameState.getTimerSeconds());
    }
}
