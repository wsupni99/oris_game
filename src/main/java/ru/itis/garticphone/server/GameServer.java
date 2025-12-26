package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.client.PlayerState;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameServer {
    private static final int PORT = 8080;
    private final Map<Socket, Player> players = new HashMap<>();
    private final Map<Integer, GameState> rooms = new HashMap<>();
    private final Map<Integer, String> secretWords = new HashMap<>();
    private final Map<Integer, Set<Integer>> readyPlayers = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService roundScheduler = Executors.newScheduledThreadPool(1);

    public GameServer() {
        loadWords();
    }

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Game server started on port " + PORT);
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket)); // Пул потоков
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
            roundScheduler.shutdownNow();
        }
    }

    private void routeMessage(Player player, Message message) {
        if (player.isDisconnected()) {
            System.out.println("Ignoring message from disconnected player: " + player.getId());
            return;
        }

        if (message.getType() == null) {
            sendError(player, "400", "Тип сообщения не задан");
            return;
        }

        switch (message.getType()) {
            case JOIN:
                if (player.isInLobby() || player.isDisconnected()) {
                    handleJoin(player, message);
                } else {
                    sendError(player, "400", "Нельзя войти в комнату из игры");
                }
                break;

            case LEAVE:
                handleLeave(player);
                break;

            case READY:
                if (player.isInLobby()) {
                    handleReady(player, message);
                } else {
                    sendError(player, "400", "Готовность только в лобби");
                }
                break;

            case START:
                if (player.isInLobby()) {
                    handleStart(player, message);
                } else {
                    sendError(player, "400", "Старт только из лобби");
                }
                break;

            case CHAT:
                if (player.isInLobby() || player.isInGame()) {
                    handleChat(player, message);
                } else {
                    sendError(player, "400", "Чат недоступен");
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
                    }
                } else {
                    sendError(player, "400", "Игровые действия только в игре");
                }
                break;

            default:
                sendError(player, "400", "Неизвестный тип сообщения: " + message.getType());
                break;
        }
    }

    private void handleClient(Socket socket) {
        Player player = null;
        try {
            player = new Player(getNextPlayerId(), "Player" + getNextPlayerId(), socket);
            synchronized (players) {
                players.put(socket, player);
            }

            Message message;
            while ((message = player.receiveLine()) != null) {
                routeMessage(player, message);
            }
        } catch (Exception e) {
            System.out.println("Client disconnected");
        } finally {
            if (player != null) {
                handleLeave(player);
                try {
                    player.close();
                } catch (IOException ignored) {}
                synchronized (players) {
                    players.remove(player.getSocket());
                }
            }
        }
    }
    private int nextPlayerId = 1;
    private int getNextPlayerId() {
        return nextPlayerId++;
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
                // Создаем комнату только при первом игроке
                GameMode mode = GameMode.valueOf(message.getPayload());
                gameState = new GameState(roomId, mode);
                rooms.put(roomId, gameState);
                gameState.setHost(player.getId()); // Первый игрок - хост
            }
            gameState.addPlayer(player);
        }
        player.setState(PlayerState.IN_LOBBY);
        broadcastPlayersUpdate(gameState);
    }

    private void handleLeave(Player player) {
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
        StringBuilder payload = new StringBuilder();
        boolean first = true;

        for (Player p : room.getPlayers()) {
            if (!first) payload.append(",");
            first = false;
            payload.append("\"").append(Message.escape(p.getName())).append("\":");
            payload.append(room.getReadyPlayers().contains(p.getId()));
        }

        Message msg = new Message(MessageType.PLAYER_STATUS, room.getRoomId(), 0, "SERVER",
                "{\"players\": {" + payload + "}}");

        for (Player p : room.getPlayers()) {
            p.send(msg);
        }
    }



    private void handleChat(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Комната не найдена");
            return;
        }

        String payload = message.getPayload() == null ? "" : message.getPayload();
        Message response = new Message(
                MessageType.CHAT,
                roomId,
                from.getId(),
                from.getName(),
                payload
        );
        String json = toJson(response);

        for (Player player : room.getPlayers()) {
            player.sendLine(json);
        }
    }

    private void handleDraw(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Комната не найдена");
            return;
        }

        if (room.getMode() == GameMode.DEAF_PHONE) {
            if (message.getPayload() == null) {
                sendError(from, "400", "Пустой рисунок");
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

        String json = toJson(response);
        for (Player player : room.getPlayers()) {
            player.sendLine(json);
        }
    }

    private void handleGuess(Player from, Message message) {
        int roomId = message.getRoomId();
        String guess = message.getPayload();
        String secret = secretWords.get(roomId);
        if (secret == null || guess == null || guess.isBlank()) {
            sendError(from, "400", "Пустое предположение или слово не задано");
            return;
        }

        if (secret.equalsIgnoreCase(guess.trim())) {
            String payload = "{\"correctPlayer\":\"" + escape(from.getName()) +
                    "\",\"word\":\"" + escape(secret) + "\",\"score\":1}";
            Message correct = new Message(
                    MessageType.CORRECT,
                    roomId,
                    from.getId(),
                    from.getName(),
                    payload
            );

            GameState room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            String json = toJson(correct);
            for (Player player : room.getPlayers()) {
                player.sendLine(json);
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
            sendError(player, "403", "Only host can start game");
            return;
        }
        if (!room.allReady()) {
            sendError(player, "412", "Not enough ready players");
            return;
        }

        int roundDuration = 60; // по умолчанию
        try {
            roundDuration = Integer.parseInt(message.getPayload());
        } catch (NumberFormatException ignored) {}

        room.setTimerSeconds(roundDuration);
        room.resetRound();

        if (room.getMode() == GameMode.GUESS_DRAWING) {
            String word = generateWord();
            secretWords.put(roomId, word);
        } else if (room.getMode() == GameMode.DEAF_PHONE) {
            room.clearChains();
        }

        String payload = String.format("roundDuration:%d,totalPlayers:%d,stage:%s",
                roundDuration, room.getPlayers().size(),
                room.getMode() == GameMode.GUESS_DRAWING ? "DRAW" : "TEXT_SUBMIT");

        Message start = new Message(MessageType.START, roomId, player.getId(),
                player.getName(), payload);
        String json = toJson(start);

        for (Player p : room.getPlayers()) {
            p.sendLine(json);
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
            String payload = "{\"word\":\"" + escape(secret) + "\"}";
            Message end = new Message(
                    MessageType.ROUND_UPDATE,
                    roomId,
                    0,
                    "SERVER",
                    payload
            );
            String json = toJson(end);
            for (Player p : room.getPlayers()) {
                p.sendLine(json);
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
            sendError(from, "404", "Комната не найдена");
            return;
        }

        if (room.getMode() != GameMode.DEAF_PHONE) {
            sendError(from, "400", "Неверный режим для TEXT_SUBMIT");
            return;
        }

        if (message.getPayload() == null || message.getPayload().isBlank()) {
            sendError(from, "400", "Пустой текст");
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

        String payload = "{\"content\":\"" + escape(message.getPayload()) +
                "\",\"contentType\":\"TEXT\",\"roundNumber\":" + room.getRound() + "}";

        Message update = new Message(
                MessageType.ROUND_UPDATE,
                roomId,
                from.getId(),
                from.getName(),
                payload
        );

        String json = toJson(update);
        next.sendLine(json);
    }

    private void sendFinalChains(GameState room) {
        int roomId = room.getRoomId();
        Map<Integer, List<ChainStep>> chains = room.getChains();

        List<ChainStep> steps = chains.values().iterator().next();

        StringBuilder chainJson = new StringBuilder();
        chainJson.append("[");

        boolean first = true;
        for (ChainStep step : steps) {
            if (!first) {
                chainJson.append(",");
            }
            first = false;
            if (step.isTextStep()) {
                chainJson.append("{\"type\":\"TEXT\",\"value\":\"")
                        .append(Message.escape(step.getText()))
                        .append("\"}");
            } else {
                String enc = java.util.Base64.getEncoder()
                        .encodeToString(step.getDrawing());
                chainJson.append("{\"type\":\"DRAW\",\"value\":\"")
                        .append(enc)
                        .append("\"}");
            }
        }
        chainJson.append("]");

        String payload = "{\"contentType\":\"FINAL_CHAIN\",\"chain\":" + chainJson + "}";

        Message chainMsg = new Message(
                MessageType.FINAL_CHAIN,
                roomId,
                0,
                "SERVER",
                payload
        );
        for (Player p : room.getPlayers()) {
            p.send(chainMsg);
        }
    }

    private void sendError(Player player, String code, String message) {
        Message error = new Message(
                MessageType.ERROR,
                0,
                0,
                "SERVER",
                "{\"code\":\"" + Message.escape(code) + "\",\"message\":\"" + Message.escape(message) + "\"}"
        );
        try {
            player.send(error);
        } catch (Exception ignored) {}
    }

    private String toJson(Message message) {
        return Message.toJson(message);
    }

    private String escape(String value) {
        return Message.escape(value);
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
            words.addAll(Arrays.asList("кот", "дом", "дерево", "машина", "солнце"));
        }
    }
}
