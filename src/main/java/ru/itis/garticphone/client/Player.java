package ru.itis.garticphone.client;

import ru.itis.garticphone.common.JsonMessageConnection;
import ru.itis.garticphone.common.Message;
import java.io.IOException;
import java.net.Socket;

public class Player {
    private final int id;
    private String name;
    private final JsonMessageConnection connection;
    private PlayerState state = PlayerState.IN_LOBBY;


    public Player(int id, String name, Socket socket) throws IOException {
        this.id = id;
        this.name = name;
        this.state = PlayerState.CONNECTED;
        this.connection = new JsonMessageConnection(socket);
    }

    // Тестовый конструктор
    public Player(int id, String name) {
        this.id = id;
        this.name = name;
        this.state = PlayerState.CONNECTED;
        this.connection = null;
    }

    public void sendLine(String json) {
        try {
            Message msg = Message.parse(json);
            connection.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(Message message) {
        if (connection != null) {
            try {
                connection.send(message);
            } catch (IOException ignored) {}
        }
    }

    public Message receiveLine() throws IOException, ClassNotFoundException {
        return connection.receive();
    }

    public void close() throws IOException {
        connection.close();
    }

    public Socket getSocket() {
        return connection != null ? connection.getSocket() : null;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlayerState getState() {
        return state;
    }

    public void setState(PlayerState state) {
        this.state = state;
    }
    public boolean isInLobby() {
        return state == PlayerState.IN_LOBBY;
    }
    public boolean isInGame() {
        return state == PlayerState.IN_GAME;
    }

    public boolean isDisconnected() {
        return state == PlayerState.DISCONNECTED;
    }

    public boolean isConnected() {
        return state == PlayerState.CONNECTED;
    }
}
