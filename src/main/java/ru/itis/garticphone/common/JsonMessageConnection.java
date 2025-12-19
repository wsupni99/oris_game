package ru.itis.garticphone.common;

import java.io.*;
import java.net.Socket;

public class JsonMessageConnection implements Closeable {

    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    public JsonMessageConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public void send(Message message) throws IOException {
        String json = toJson(message);
        out.writeObject(json);
        out.flush();
    }

    public Message receive() throws IOException, ClassNotFoundException {
        Object obj = in.readObject();
        if (!(obj instanceof String)) {
            throw new IOException("Unexpected object type: " + obj.getClass());
        }
        String json = (String) obj;
        return parse(json);
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        try {
            in.close();
        } catch (IOException ignored) {
        }
        try {
            out.close();
        } catch (IOException ignored) {
        }
        socket.close();
    }

    private Message parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        Message message = new Message();
        try {
            String body = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = body.split(",\"");

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