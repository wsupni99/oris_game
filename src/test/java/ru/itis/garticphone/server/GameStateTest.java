package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;

import static org.junit.jupiter.api.Assertions.*;

class GameStateTest {

    @Test
    void shouldAddAndRemovePlayers() {
        GameState gameState = new GameState(1, GameMode.GUESS_DRAW);

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
        GameState gameState = new GameState(1, GameMode.TELEPHONE);

        assertEquals(1, gameState.getRound());
        gameState.nextRound();
        gameState.nextRound();
        assertEquals(3, gameState.getRound());

        gameState.resetRound();
        assertEquals(1, gameState.getRound());
    }

    @Test
    void timerShouldDecreaseButNotBelowZero() {
        GameState gameState = new GameState(1, GameMode.GUESS_DRAW);

        gameState.setTimerSeconds(2);
        gameState.decrementTimer();
        assertEquals(1, gameState.getTimerSeconds());
        gameState.decrementTimer();
        assertEquals(0, gameState.getTimerSeconds());
        gameState.decrementTimer();
        assertEquals(0, gameState.getTimerSeconds());
    }
}
