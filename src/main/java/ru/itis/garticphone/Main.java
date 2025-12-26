package ru.itis.garticphone;

import ru.itis.garticphone.common.JsonMessageConnection;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8080;

        Scanner scanner = new Scanner(System.in);
        System.out.print("Player name (Enter = Danya): ");
        String playerName = scanner.nextLine().trim();
        if (playerName.isBlank()) {
            playerName = "Danya";
        }

        try (Socket socket = new Socket(host, port);
             JsonMessageConnection connection = new JsonMessageConnection(socket)) {

            System.out.println("Connected to server " + host + ":" + port);

            Thread listener = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        Message msg = connection.receive();
                        if (msg == null) {
                            break;
                        }
                        System.out.println("← " + Message.toJson(msg));
                    }
                } catch (Exception e) {
                    System.out.println("Connection closed");
                }
            });
            listener.setDaemon(true);
            listener.start();

            System.out.println("Commands:");
            System.out.println("join <roomId> <GUESS_DRAWING|DEAF_PHONE>");
            System.out.println("ready");
            System.out.println("start <seconds>");
            System.out.println("chat <text>");
            System.out.println("guess <word>");
            System.out.println("leave");
            System.out.println("exit");

            int roomId = 1;
            int playerId = 0;

            while (true) {
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("exit")) {
                    break;
                }
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+", 3);
                String cmd = parts[0].toLowerCase();

                try {
                    switch (cmd) {
                        case "join" -> {
                            roomId = Integer.parseInt(parts[1]);
                            String mode = parts[2];
                            Message msg = new Message(
                                    MessageType.JOIN,
                                    roomId,
                                    playerId,
                                    playerName,
                                    mode
                            );
                            connection.send(msg);
                        }
                        case "ready" -> {
                            Message msg = new Message(
                                    MessageType.READY,
                                    roomId,
                                    playerId,
                                    playerName,
                                    ""
                            );
                            connection.send(msg);
                        }
                        case "start" -> {
                            String seconds = parts[1];
                            Message msg = new Message(
                                    MessageType.START,
                                    roomId,
                                    playerId,
                                    playerName,
                                    seconds
                            );
                            connection.send(msg);
                        }
                        case "chat" -> {
                            String text = parts.length >= 2 ? line.substring(cmd.length()).trim() : "";
                            Message msg = new Message(
                                    MessageType.CHAT,
                                    roomId,
                                    playerId,
                                    playerName,
                                    text
                            );
                            connection.send(msg);
                        }
                        case "guess" -> {
                            String word = parts.length >= 2 ? line.substring(cmd.length()).trim() : "";
                            Message msg = new Message(
                                    MessageType.GUESS,
                                    roomId,
                                    playerId,
                                    playerName,
                                    word
                            );
                            connection.send(msg);
                        }
                        case "leave" -> {
                            Message msg = new Message(
                                    MessageType.LEAVE,
                                    roomId,
                                    playerId,
                                    playerName,
                                    ""
                            );
                            connection.send(msg);
                        }
                        default -> System.out.println("Unknown command");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
