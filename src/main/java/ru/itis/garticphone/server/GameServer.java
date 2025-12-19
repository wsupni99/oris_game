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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {

    private static final int PORT = 8080;

    private final Map<Socket, Player> players = new HashMap<>();
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
            case CHAT:
                broadcastChat(player, message.getPayload());
                break;
            default:
                break;
        }
    }

    private void broadcastChat(Player from, String text) {
        String payload = text == null ? "" : text;
        Message response = new Message(
                MessageType.CHAT,
                0,
                from.getId(),
                from.getName(),
                payload
        );
        String json = toJson(response);
        synchronized (players) {
            for (Player player : players.values()) {
                player.sendLine(json);
            }
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
