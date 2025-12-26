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
        out.writeObject(Message.toJson(message));
        out.flush();
    }

    public Message receive() throws IOException, ClassNotFoundException {
        Object obj = in.readObject();
        if (!(obj instanceof String)) {
            throw new IOException("Unexpected object type: " + obj.getClass());
        }
        return Message.parse((String) obj);
    }

    public Socket getSocket() {
        return socket;
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

    public boolean isOpen() {
        return !socket.isClosed() && socket.isConnected();
    }

}