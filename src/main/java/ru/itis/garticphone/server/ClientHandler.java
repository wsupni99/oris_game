package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;

import java.io.IOException;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Player player;
    private final GameService gameService;

    public ClientHandler(Socket socket, int playerId, GameService gameService) throws IOException {
        this.socket = socket;
        this.player = new Player(playerId, "Player" + playerId, socket);
        this.gameService = gameService;
    }

    @Override
    public void run() {
        try {
            gameService.onConnect(player);

            Message message;
            while ((message = player.receiveLine()) != null) {
                gameService.routeMessage(player, message);
            }
        } catch (Exception e) {
            System.out.println("Client disconnected: " + player.getId());
        } finally {
            gameService.onDisconnect(player);
            try {
                player.close();
            } catch (IOException ignored) {
            }
        }
    }

    public Player getPlayer() {
        return player;
    }

    public Socket getSocket() {
        return socket;
    }
}