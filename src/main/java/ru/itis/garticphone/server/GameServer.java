package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
// TODO: Управление логикой входа, выбора режима, ожидания, готовности и старта
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



    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Game server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
            roundScheduler.shutdownNow();
        }
    }

    private void handleClient(Socket socket) {
        Player player = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            int playerId = getNextPlayerId();
            player = new Player(playerId, "Player" + playerId, socket);

            synchronized (players) {
                players.put(socket, player);
            }

            System.out.println("Player connected: " + playerId);

            String line;
            while ((line = in.readLine()) != null) {
                Message message = parseMessage(line);
                if (message == null) {
                    sendError(out, "400", "Некорректное сообщение");
                    continue;
                }
                routeMessage(player, message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected");
        } finally {
            if (player != null) {
                handleLeave(player);
                player.close();
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

    private void routeMessage(Player player, Message message) {
        if (message.getType() == null) {
            sendError(getWriter(player), "400", "Тип сообщения не задан");
            return;
        }
        switch (message.getType()) {
            case JOIN:
                handleJoin(player, message);
                break;
            case CHAT:
                handleChat(player, message);
                break;
            case DRAW:
                handleDraw(player, message);
                break;
            case GUESS:
                handleGuess(player, message);
                break;
            case READY:
                handleReady(player, message);
                break;
            case START:
                handleStart(player, message);
                break;
            case TEXT_SUBMIT:
                handleTextSubmit(player, message);
                break;
            case LEAVE:
                handleLeave(player);
                break;
            default:
                sendError(getWriter(player), "400", "Неизвестный тип сообщения: " + message.getType());
                break;
        }
    }

    private PrintWriter getWriter(Player player) {
        try {
            return new PrintWriter(player.getSocket().getOutputStream(), true);
        } catch (IOException e) {
            return new PrintWriter(System.out, true);
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
                gameState = new GameState(roomId, GameMode.GUESS_DRAWING);
                rooms.put(roomId, gameState);
            }
            gameState.addPlayer(player);
        }

        broadcastPlayersUpdate(gameState);
    }

    private void handleLeave(Player player) {
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
        payload.append("[");
        boolean first = true;
        for (Player p : room.getPlayers()) {
            if (!first) {
                payload.append(",");
            }
            first = false;
            payload.append("\"").append(escape(p.getName())).append("\"");
        }
        payload.append("]");

        Message msg = new Message(
                MessageType.CHAT,
                room.getRoomId(),
                0,
                "SERVER",
                payload.toString()
        );

        String json = toJson(msg);
        for (Player p : room.getPlayers()) {
            p.sendLine(json);
        }
    }

    private void handleChat(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(getWriter(from), "404", "Комната не найдена");
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
            sendError(getWriter(from), "404", "Комната не найдена");
            return;
        }

        if (room.getMode() == GameMode.DEAF_PHONE) {
            if (message.getPayload() == null) {
                sendError(getWriter(from), "400", "Пустой рисунок");
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
            sendError(getWriter(from), "400", "Пустое предположение или слово не задано");
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
        readyPlayers.computeIfAbsent(roomId, id -> new HashSet<>()).add(player.getId());
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(getWriter(player), "404", "Комната не найдена");
            return;
        }

        int total = room.getPlayers().size();
        int ready = readyPlayers.get(roomId).size();
        String payload = "{\"ready\":" + ready + ",\"total\":" + total + "}";

        Message info = new Message(
                MessageType.READY,
                roomId,
                player.getId(),
                player.getName(),
                payload
        );
        String json = toJson(info);
        for (Player p : room.getPlayers()) {
            p.sendLine(json);
        }
    }

    private void handleStart(Player player, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(getWriter(player), "404", "Комната не найдена");
            return;
        }

        int roundDuration = 60;
        room.setTimerSeconds(roundDuration);
        room.resetRound();

        if (room.getMode() == GameMode.GUESS_DRAWING) {
            String word = generateWord();
            secretWords.put(roomId, word);
        } else if (room.getMode() == GameMode.DEAF_PHONE) {
            room.clearChains();
        }

        String payload = "{\"roundDuration\":" + roundDuration +
                ",\"totalPlayers\":" + room.getPlayers().size() +
                ",\"stage\":1}";

        Message start = new Message(
                MessageType.START,
                roomId,
                player.getId(),
                player.getName(),
                payload
        );

        String json = toJson(start);
        for (Player p : room.getPlayers()) {
            p.sendLine(json);
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
            sendError(getWriter(from), "404", "Комната не найдена");
            return;
        }

        if (room.getMode() != GameMode.DEAF_PHONE) {
            sendError(getWriter(from), "400", "Неверный режим для TEXT_SUBMIT");
            return;
        }

        if (message.getPayload() == null || message.getPayload().isBlank()) {
            sendError(getWriter(from), "400", "Пустой текст");
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
                        .append(escape(step.getText()))
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
                MessageType.ROUND_UPDATE,
                roomId,
                0,
                "SERVER",
                payload
        );
        String json = toJson(chainMsg);
        for (Player p : room.getPlayers()) {
            p.sendLine(json);
        }
    }

    private void sendError(PrintWriter out, String code, String message) {
        String payload = "{\"code\":\"" + code + "\",\"message\":\"" + escape(message) + "\"}";
        Message error = new Message(
                MessageType.ERROR,
                0,
                0,
                "SERVER",
                payload
        );
        out.println(toJson(error));
    }

    private Message parseMessage(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        Message message = new Message();
        try {
            String trimmed = line.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return null;
            }

            String body = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = body.split(",\"");

            for (String part : parts) {
                int colonIndex = part.indexOf(':');
                if (colonIndex <= 0) {
                    continue;
                }

                String rawKey = part.substring(0, colonIndex);
                String value = part.substring(colonIndex + 1);

                String key = rawKey.replace("\"", "");
                String cleaned = stripQuotes(value);

                switch (key) {
                    case "type":
                        message.setType(MessageType.valueOf(cleaned));
                        break;
                    case "roomId":
                        message.setRoomId(Integer.parseInt(cleaned));
                        break;
                    case "playerId":
                        message.setPlayerId(Integer.parseInt(cleaned));
                        break;
                    case "playerName":
                        message.setPlayerName(unescape(cleaned));
                        break;
                    case "payload":
                        message.setPayload(unescape(cleaned));
                        break;
                    default:
                        break;
                }
            }
            return message;
        } catch (Exception e) {
            return null;
        }
    }

    private String stripQuotes(String value) {
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\\\", "\\")
                .replace("\\\"", "\"");
    }

    private String toJson(Message message) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(message.getType().name()).append("\",");
        sb.append("\"roomId\":").append(message.getRoomId()).append(",");
        sb.append("\"playerId\":").append(message.getPlayerId()).append(",");
        sb.append("\"playerName\":\"").append(escape(message.getPlayerName())).append("\",");
        sb.append("\"payload\":\"").append(escape(message.getPayload())).append("\"");
        sb.append("}");
        return sb.toString();
    }
}
