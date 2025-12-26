package ru.itis.garticphone.server;

import com.google.gson.Gson;
import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.client.PlayerState;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.Base64.getEncoder;

public class GameService {
    private final Map<Integer, GameState> rooms = new HashMap<>();
    private final Map<Integer, String> secretWords = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private final ScheduledExecutorService roundScheduler;
    private final Gson gson = new Gson();

    public GameService(ScheduledExecutorService roundScheduler) {
        this.roundScheduler = roundScheduler;
        loadWords();
    }

    public void onConnect(Player player) {
        player.setState(PlayerState.CONNECTED);
    }

    public void onDisconnect(Player player) {
        handleLeave(player);
    }

    public void routeMessage(Player player, Message message) {
        if (player.isDisconnected()) {
            System.out.println("Ignoring message from disconnected player: " + player.getId());
            return;
        }

        if (message.getType() == null) {
            sendError(player, "400", "Message type is not set");
            return;
        }

        switch (message.getType()) {
            case JOIN:
                if (player.isInLobby() || player.isConnected()) {
                    handleJoin(player, message);
                } else {
                    sendError(player, "400", "Cannot join room from game state");
                }
                break;
            case LEAVE:
                handleLeave(player);
                break;
            case READY:
                if (player.isInLobby()) {
                    handleReady(player, message);
                } else {
                    sendError(player, "400", "READY is allowed only in lobby");
                }
                break;
            case START:
                if (player.isInLobby()) {
                    handleStart(player, message);
                } else {
                    sendError(player, "400", "START is allowed only from lobby");
                }
                break;
            case CHAT:
                if (player.isInLobby() || player.isInGame()) {
                    handleChat(player, message);
                } else {
                    sendError(player, "400", "Chat is not available in this state");
                }
                break;
            case DRAW:
            case GUESS:
            case TEXT_SUBMIT:
                if (player.isInGame()) {
                    switch (message.getType()) {
                        case DRAW -> handleDraw(player, message);
                        case GUESS -> handleGuess(player, message);
                        case TEXT_SUBMIT -> handleTextSubmit(player, message);
                        default -> {
                        }
                    }
                } else {
                    sendError(player, "400", "Game actions are allowed only in game");
                }
                break;
            default:
                sendError(player, "400", "Unknown message type: " + message.getType());
                break;
        }
    }

    private void handleJoin(Player player, Message message) {
        int roomId = message.getRoomId();
        String name = message.getPlayerName();

        if (name != null && !name.isBlank()) {
            player.setName(name);
        }

        GameState gameState;
        synchronized (rooms) {
            gameState = rooms.get(roomId);
            if (gameState == null) {
                GameMode mode = GameMode.valueOf(message.getPayload());
                gameState = new GameState(roomId, mode);
                rooms.put(roomId, gameState);
                gameState.setHost(player.getId());
            }
            gameState.addPlayer(player);
        }

        player.setState(PlayerState.IN_LOBBY);
        broadcastPlayersUpdate(gameState);
    }

    public void handleLeave(Player player) {
        player.setState(PlayerState.DISCONNECTED);
        synchronized (rooms) {
            for (GameState room : rooms.values()) {
                if (room.getPlayers().contains(player)) {
                    room.removePlayer(player);
                    broadcastPlayersUpdate(room);
                }
            }
        }
    }

    private void broadcastPlayersUpdate(GameState room) {
        Map<String, Boolean> playersStatus = new HashMap<>();
        for (Player p : room.getPlayers()) {
            playersStatus.put(p.getName(), room.getReadyPlayers().contains(p.getId()));
        }
        Message msg = new Message(
                MessageType.PLAYER_STATUS,
                room.getRoomId(),
                0,
                "SERVER",
                gson.toJson(playersStatus)
        );
        for (Player p : room.getPlayers()) {
            p.send(msg);
        }
    }

    private void handleChat(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        String payload = message.getPayload() != null ? message.getPayload() : "";
        Message response = new Message(
                MessageType.CHAT,
                roomId,
                from.getId(),
                from.getName(),
                payload
        );
        for (Player player : room.getPlayers()) {
            player.send(response);
        }
    }

    private void handleDraw(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        if (room.getMode() == GameMode.DEAF_PHONE) {
            if (message.getPayload() == null) {
                sendError(from, "400", "Empty drawing payload");
                return;
            }
            room.getChains()
                    .computeIfAbsent(from.getId(), id -> new ArrayList<>())
                    .add(new ChainStep(message.getPayload().getBytes()));
        }

        Message response = new Message(
                MessageType.DRAW,
                roomId,
                from.getId(),
                from.getName(),
                message.getPayload()
        );
        for (Player player : room.getPlayers()) {
            player.send(response);
        }
    }

    private void handleGuess(Player from, Message message) {
        int roomId = message.getRoomId();
        String guess = message.getPayload();
        String secret = secretWords.get(roomId);
        if (secret == null || guess == null || guess.isBlank()) {
            sendError(from, "400", "Empty guess or secret word is not set");
            return;
        }

        if (secret.equalsIgnoreCase(guess.trim())) {
            Map<String, Object> payloadData = new HashMap<>();
            payloadData.put("correctPlayer", from.getName());
            payloadData.put("word", secret);
            payloadData.put("score", 1);

            Message correct = new Message(
                    MessageType.CORRECT,
                    roomId,
                    from.getId(),
                    from.getName(),
                    gson.toJson(payloadData)
            );

            GameState room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            for (Player player : room.getPlayers()) {
                player.send(correct);
            }
            endRound(roomId);
        }
    }

    private void handleReady(Player player, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(player, "404", "Room not found");
            return;
        }
        room.toggleReady(player.getId());
        broadcastPlayersUpdate(room);
    }

    private void handleStart(Player player, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(player, "404", "Room not found");
            return;
        }

        if (!room.isHost(player.getId())) {
            sendError(player, "403", "Only host can start the game");
            return;
        }
        if (!room.allReady()) {
            sendError(player, "412", "Not enough ready players");
            return;
        }

        int roundDuration = 60;
        try {
            roundDuration = Integer.parseInt(message.getPayload());
        } catch (NumberFormatException ignored) {
        }

        room.setTimerSeconds(roundDuration);
        room.resetRound();

        if (room.getMode() == GameMode.GUESS_DRAWING) {
            String word = generateWord();
            secretWords.put(roomId, word);
        } else if (room.getMode() == GameMode.DEAF_PHONE) {
            room.clearChains();
        }

        Map<String, Object> payloadData = new HashMap<>();
        payloadData.put("roundDuration", roundDuration);
        payloadData.put("totalPlayers", room.getPlayers().size());
        payloadData.put("stage", room.getMode() == GameMode.GUESS_DRAWING ? "DRAW" : "TEXT_SUBMIT");

        Message start = new Message(
                MessageType.START,
                roomId,
                player.getId(),
                player.getName(),
                gson.toJson(payloadData)
        );

        for (Player p : room.getPlayers()) {
            p.send(start);
            p.setState(PlayerState.IN_GAME);
        }

        scheduleRoundEnd(roomId, roundDuration);
    }

    private void scheduleRoundEnd(int roomId, int roundDuration) {
        roundScheduler.schedule(() -> endRound(roomId), roundDuration, TimeUnit.SECONDS);
    }

    private void endRound(int roomId) {
        GameState room;
        synchronized (rooms) {
            room = rooms.get(roomId);
        }
        if (room == null) {
            return;
        }

        if (room.getMode() == GameMode.GUESS_DRAWING) {
            String secret = secretWords.get(roomId);
            if (secret == null) {
                return;
            }
            Map<String, String> payloadData = new HashMap<>();
            payloadData.put("word", secret);
            Message end = new Message(
                    MessageType.ROUND_UPDATE,
                    roomId,
                    0,
                    "SERVER",
                    gson.toJson(payloadData)
            );
            for (Player p : room.getPlayers()) {
                p.send(end);
            }
        } else if (room.getMode() == GameMode.DEAF_PHONE) {
            sendFinalChains(room);
        }
    }

    public String generateWord() {
        return words.get((int) (Math.random() * words.size()));
    }

    private void handleTextSubmit(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        if (room.getMode() != GameMode.DEAF_PHONE) {
            sendError(from, "400", "Invalid mode for TEXT_SUBMIT");
            return;
        }

        if (message.getPayload() == null || message.getPayload().isBlank()) {
            sendError(from, "400", "Text payload is empty");
            return;
        }

        List<Player> list = room.getPlayers();
        int index = list.indexOf(from);
        if (index == -1) {
            return;
        }

        room.getChains()
                .computeIfAbsent(from.getId(), id -> new ArrayList<>())
                .add(new ChainStep(message.getPayload()));

        int nextIndex = (index + 1) % list.size();
        Player next = list.get(nextIndex);

        Map<String, Object> payloadData = new HashMap<>();
        payloadData.put("content", message.getPayload());
        payloadData.put("contentType", "TEXT");
        payloadData.put("roundNumber", room.getRound());

        Message update = new Message(
                MessageType.ROUND_UPDATE,
                roomId,
                from.getId(),
                from.getName(),
                gson.toJson(payloadData)
        );

        next.send(update);
    }

    private void sendFinalChains(GameState room) {
        int roomId = room.getRoomId();
        List<ChainStep> steps = new ArrayList<>(room.getChains().values().iterator().next());

        List<Map<String, Object>> chain = steps.stream()
                .map(step -> {
                    Map<String, Object> link = new HashMap<>();
                    link.put("type", step.isTextStep() ? "TEXT" : "DRAW");
                    link.put("value", step.isTextStep()
                            ? step.getText()
                            : getEncoder().encodeToString(step.getDrawing()));
                    return link;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("contentType", "FINAL_CHAIN");
        payload.put("chain", chain);

        Message chainMsg = new Message(
                MessageType.FINAL_CHAIN,
                roomId,
                0,
                "SERVER",
                gson.toJson(payload)
        );
        for (Player p : room.getPlayers()) {
            p.send(chainMsg);
        }
    }

    private void sendError(Player player, String code, String message) {
        Map<String, String> errorData = new HashMap<>();
        errorData.put("code", code);
        errorData.put("message", message);
        Message error = new Message(
                MessageType.ERROR,
                0,
                0,
                "SERVER",
                gson.toJson(errorData)
        );
        try {
            player.send(error);
        } catch (Exception ignored) {
        }
    }

    private void loadWords() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                GameServer.class.getResourceAsStream("/words.txt")
                        ),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    words.add(trimmed);
                }
            }
        } catch (Exception e) {
            words.clear();
            words.addAll(Arrays.asList("cat", "house", "tree", "car", "sun"));
        }
    }
}
