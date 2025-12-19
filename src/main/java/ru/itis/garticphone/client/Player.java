package ru.itis.garticphone.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Player {
    private final int id;
    private String name;
    private PlayerState state;
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public Player(int id, String name, Socket socket) throws IOException {
        this.id = id;
        this.name = name;
        this.socket = socket;
        this.state = PlayerState.CONNECTED;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Для тестов конструктор без сокета
    public Player(int id, String name) {
        this.id = id;
        this.name = name;
        this.socket = null;
        this.state = PlayerState.CONNECTED;
        this.in = null;
        this.out = null;
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

    public Socket getSocket() {
        return socket;
    }

    public BufferedReader getIn() {
        return in;
    }

    public PrintWriter getOut() {
        return out;
    }

    public void sendLine(String line) {
        out.println(line);
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
