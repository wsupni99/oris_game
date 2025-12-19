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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {

    private static final int PORT = 8080;

    private final Map<Socket, Player> players = new HashMap<>();
    private final Map<Integer, GameState> rooms = new HashMap<>();
    private final Map<Integer, String> secretWords = new HashMap<>();
    private final Map<Integer, Set<Integer>> readyPlayers = new HashMap<>();

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private int nextPlayerId = 1;

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

    private int getNextPlayerId() {
        return nextPlayerId++;
    }

    private void routeMessage(Player player, Message message) {
        if (message.getType() == null) {
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
                gameState = new GameState(roomId, GameMode.GUESS_DRAW);
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
            return;
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
        if (secret == null || guess == null) {
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
        }
    }

    private void handleReady(Player player, Message message) {
        int roomId = message.getRoomId();
        readyPlayers.computeIfAbsent(roomId, id -> new HashSet<>()).add(player.getId());
        GameState room = rooms.get(roomId);
        if (room == null) {
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
            return;
        }

        int roundDuration = 60;
        room.setTimerSeconds(roundDuration);
        room.resetRound();

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
    }

    private void handleTextSubmit(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            return;
        }

        List<Player> list = room.getPlayers();
        int index = list.indexOf(from);
        if (index == -1) {
            return;
        }
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
            String[] parts = trimmed.substring(1, trimmed.length() - 1).split(",\"");
            for (String part : parts) {
                String p = part.replace("\"", "");
                String[] kv = p.split(":", 2);
                if (kv.length != 2) {
                    continue;
                }
                String key = kv[0];
                String value = kv[1];
                switch (key) {
                    case "type":
                        message.setType(MessageType.valueOf(value));
                        break;
                    case "roomId":
                        message.setRoomId(Integer.parseInt(value));
                        break;
                    case "playerId":
                        message.setPlayerId(Integer.parseInt(value));
                        break;
                    case "playerName":
                        message.setPlayerName(value);
                        break;
                    case "payload":
                        message.setPayload(value);
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

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
