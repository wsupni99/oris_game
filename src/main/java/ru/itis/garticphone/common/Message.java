package ru.itis.garticphone.common;

public class Message {
    private MessageType type;
    private int roomId;
    private int playerId;
    private String playerName;
    private String payload;

    public Message() {
    }

    public Message(MessageType type, int roomId, int playerId, String playerName, String payload) {
        this.type = type;
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
