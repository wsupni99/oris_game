package ru.itis.garticphone;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Main {
    private static int playerId;
    private static String playerName = "TestPlayer";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Имя игрока (Enter=Danya): ");
        String name = scanner.nextLine().trim();
        if (!name.isEmpty()) playerName = name;

        try (Socket socket = new Socket("localhost", 8080)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Слушаем сервер в отдельном потоке
            Thread listener = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("← Сервер: " + line);
                    }
                } catch (IOException e) {
                    System.out.println("Сервер отключился");
                }
            });
            listener.start();

            System.out.println("Подключен! Команды:");
            System.out.println("JOIN 1 GUESSDRAWING  → создать/войти в угадайку");
            System.out.println("JOIN 1 DEAFPHONE     → глухой телефон");
            System.out.println("READY                → готов");
            System.out.println("START 60             → старт (60 сек)");
            System.out.println("CHAT hello           → чат");
            System.out.println("exit                 → выход");

            while (true) {
                String input = scanner.nextLine().trim();
                if (input.equals("exit")) break;

                String[] parts = input.split("\\s+", 3);
                String cmd = parts[0];

                switch (cmd) {
                    case "JOIN" -> {
                        int roomId = Integer.parseInt(parts[1]);
                        String mode = parts[2];
                        Message msg = new Message(MessageType.JOIN, roomId, 0, playerName, mode);
                        out.println(toJson(msg));
                    }
                    case "READY" -> {
                        Message msg = new Message(MessageType.READY, 1, playerId, playerName, "");
                        out.println(toJson(msg));
                    }
                    case "START" -> {
                        Message msg = new Message(MessageType.START, 1, playerId, playerName, parts[1]);
                        out.println(toJson(msg));
                    }
                    case "CHAT" -> {
                        Message msg = new Message(MessageType.CHAT, 1, playerId, playerName, parts[1]);
                        out.println(toJson(msg));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String toJson(Message msg) {
        return String.format("{type:%s,roomId:%d,playerId:%d,playerName:%s,payload:%s}",
                msg.getType(), msg.getRoomId(), msg.getPlayerId(),
                escape(msg.getPlayerName()), escape(msg.getPayload()));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
