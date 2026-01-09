package com.example.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 6969;
    private static final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();

    // Listeners for WebSocket connections.
    private static final Set<RoomBroadcastListener> roomListeners = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        RoomManager.init();
        List<String> existingRooms = RoomManager.getAllRooms();
        for (String r : existingRooms) {
            rooms.put(r, ConcurrentHashMap.newKeySet());
            for (String u : RoomManager.getUsersInRoom(r)) {
                rooms.get(r).add(u);
            }
        }
        System.out.println("Loaded rooms from DB: " + existingRooms);

        WebSocketBridge wsBridge = new WebSocketBridge(8080);
        wsBridge.start();
        System.out.println("WebSocket bridge listening on port 8080");

        System.out.println("Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            ExecutorService pool = Executors.newCachedThreadPool();
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                pool.execute(clientHandler);
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void addUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
        broadcast("> " + username + " joined the chat!");
    }

    public static void removeUser(String username) {
        onlineUsers.remove(username);
        broadcast("> " + username + " left the chat!");
    }

    public static void broadcast(String message) {
        for (ClientHandler client : onlineUsers.values()) {
            client.sendMessage(message);
        }
    }

    public static void sendPrivate(String toUser, String message) {
        ClientHandler receiver = onlineUsers.get(toUser);
        if (receiver != null) {
            receiver.sendMessage(message);
        }
    }

    public static Set<String> getOnlineUsernames() {
        return onlineUsers.keySet();
    }

    public static void logMessage(String message, ClientHandler sender) {
        System.out.println(sender.getName() + ": " + message);
    }

    public static void joinRoom(String username, String roomName) {
        RoomManager.addUserToRoom(username, roomName);
        rooms.computeIfAbsent(roomName, k -> ConcurrentHashMap.newKeySet()).add(username);
        broadcastRoom(roomName, "> " + username + " joined.", username);
    }

    public static void leaveRoom(String username, String roomName) {
        RoomManager.removeUserFromRoom(username, roomName);
        Set<String> members = rooms.get(roomName);
        if (members != null) {
            members.remove(username);
            if (members.isEmpty()) rooms.remove(roomName);
            broadcastRoom(roomName, "> " + username + " left.", username);
        }
    }

    public static Set<String> getRoomMembers(String roomName) {
        return rooms.getOrDefault(roomName, Collections.emptySet());
    }

    public static Set<String> getRooms() {
        return rooms.keySet();
    }

    public static void broadcastRoom(String roomName, String message, String username) {
        if (!message.startsWith("> ")) {
            MessageManager.saveMessage(roomName, username, message.substring(username.length() + 2));
        }

        Set<String> members = rooms.get(roomName);
        if (members == null) return;

        // se
        for (String user : members) {
            ClientHandler handler = onlineUsers.get(user);
            if (handler != null) handler.sendMessage(message);
        }

        for (RoomBroadcastListener l : roomListeners) {
            try {
                l.onRoomMessage(roomName, message, username);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // WebSocket methods
    public static void addRoomBroadcastListener(RoomBroadcastListener l) {
        roomListeners.add(l);
    }

    public static void removeBroadcastListener(RoomBroadcastListener l) {
        roomListeners.remove(l);
    }
}
