package ru.itis.garticphone.common;

public class Message {
    public MessageType type;
    public String roomId;
    public String playerId;
    public String playerName;
    public Object payload;

    public Message() {}

    public Message(MessageType type, String roomId, String playerId, String playerName, Object payload) {
        this.type = type;
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.payload = payload;
    }
}