package com.example.server;

public interface RoomBroadcastListener {
    void onRoomMessage(String roomName, String message, String sender);
}
