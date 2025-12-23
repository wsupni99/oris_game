package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;

import java.util.*;

public class GameState {
    private final int roomId;
    private GameMode mode;
    private final List<Player> players;
    private int round;
    private int timerSeconds;
    private final Map<Integer, List<ChainStep>> chains = new HashMap<>();
    private final Set<Integer> readyPlayers = new HashSet<>();
    private final int minPlayers;
    private int hostId = -1;
    private String currentStage = "LOBBY";

    public GameState(int roomId, GameMode mode) {
        this.roomId = roomId;
        this.mode = mode;
        this.minPlayers = mode == GameMode.GUESS_DRAWING ? 2 : 4;
        this.players = new ArrayList<>();
        this.round = 1;
        this.timerSeconds = 0;
    }

    public int getRoomId() {
        return roomId;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public int getRound() {
        return round;
    }

    public void nextRound() {
        round++;
    }

    public void resetRound() {
        round = 1;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public void decrementTimer() {
        if (timerSeconds > 0) {
            timerSeconds--;
        }
    }

    public Map<Integer, List<ChainStep>> getChains() {
        return chains;
    }

    public void clearChains() {
        chains.clear();
    }

    public void setHost(int playerId) {
        this.hostId = playerId;
    }

    public int getHostId() {
        return hostId;
    }

    public boolean isHost(int playerId) {
        return playerId == hostId;
    }

    public void toggleReady(int playerId) {
        if (readyPlayers.contains(playerId)) readyPlayers.remove(playerId);
        else readyPlayers.add(playerId);
    }

    public boolean allReady() {
        return players.size() >= minPlayers &&
                readyPlayers.size() == players.size();
    }

    public Set<Integer> getReadyPlayers() {
        return readyPlayers;
    }

    public String getCurrentStage() {
        return currentStage;
    }

    public void setCurrentStage(String stage) {
        this.currentStage = stage;
    }
}
