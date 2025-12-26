package ru.itis.garticphone.client;

import ru.itis.garticphone.common.JsonMessageConnection;
import ru.itis.garticphone.common.Message;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientConnection implements Closeable {
    private final Socket socket;
    private final JsonMessageConnection connection;

    public ClientConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.connection = new JsonMessageConnection(socket);
    }

    public void send(Message message) throws IOException {
        connection.send(message);
    }

    public void startListening(Consumer<Message> handler) {
        Thread t = new Thread(() -> {
            try {
                Message msg;
                while ((msg = connection.receive()) != null) {
                    handler.accept(msg);
                }
            } catch (Exception ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
