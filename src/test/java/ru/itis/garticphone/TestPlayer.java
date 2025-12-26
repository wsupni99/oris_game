package ru.itis.garticphone;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.common.Message;

import java.util.ArrayList;
import java.util.List;

public class TestPlayer extends Player {
    private final List<Message> sent = new ArrayList<>();

    public TestPlayer(int id, String name) {
        super(id, name);
    }

    @Override
    public void send(Message message) {
        sent.add(message);
    }

    public List<Message> getSent() {
        return sent;
    }
}
