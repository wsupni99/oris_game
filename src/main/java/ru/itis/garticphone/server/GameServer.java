package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GameServer {

    private static final int PORT = 8080;

    private final Map<Socket, Player> players = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledExecutorService roundScheduler = Executors.newScheduledThreadPool(1);
    private final GameService gameService = new GameService(roundScheduler);

    private int nextPlayerId = 1;

    public static void main(String[] args) {
        new GameServer().start();
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Game server started on port " + PORT);
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                int id = getNextPlayerId();
                ClientHandler handler = new ClientHandler(clientSocket, id, gameService);
                synchronized (players) {
                    players.put(clientSocket, handler.getPlayer());
                }
                executorService.submit(handler); // Пул потоков
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            executorService.shutdownNow();
            roundScheduler.shutdownNow();
        }
    }

    private int getNextPlayerId() {
        return nextPlayerId++;
    }
}
