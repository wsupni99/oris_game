package ru.itis.garticphone.common;

import java.io.*;
import java.net.Socket;

public class JsonMessageConnection implements Closeable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public JsonMessageConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream())
        );
        this.reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream())
        );
    }

    public void send(Message message) throws IOException {
        writer.write(Message.toJson(message));
        writer.newLine();
        writer.flush();
    }

    public Message receive() throws IOException {
        String line = reader.readLine();
        if (line == null) return null;
        return Message.parse(line);
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void close() throws IOException {
        try {
            reader.close();
        } catch (IOException ignored) {
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        socket.close();
    }

    public boolean isOpen() {
        return !socket.isClosed() && socket.isConnected();
    }

}