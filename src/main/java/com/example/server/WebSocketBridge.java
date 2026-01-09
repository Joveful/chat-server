package com.example.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketBridge extends WebSocketServer implements RoomBroadcastListener {

    private static final Gson gson = new Gson();
    private final Map<WebSocket, String> wsToUser = new ConcurrentHashMap<>();
    private final Map<String, WebSocket> userToWs = new ConcurrentHashMap<>();
    private final Map<String, String> userRoom = new ConcurrentHashMap<>();

    public WebSocketBridge(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        sendSystem(conn, "welcome", "Connected to chat WebSocket bridge.");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String user = wsToUser.remove(conn);
        if (user != null) {
            userToWs.remove(user);
            String room = userRoom.remove(user);
            if (room != null) {
                ChatServer.leaveRoom(user, room);
                RoomManager.removeUserFromRoom(user, room);
            }
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject obj = gson.fromJson(message, JsonObject.class);
            String type = obj.has("type") ? obj.get("type").getAsString() : "";

            switch (type) {
                case "register" -> handleRegister(conn, obj);
                case "login" -> handleLogin(conn, obj);
                case "join" -> handleJoin(conn, obj);
                case "leave" -> handleLeave(conn, obj);
                case "message" -> handleMessage(conn, obj);
                case "rooms" -> handleRooms(conn);
                case "who" -> handleWho(conn);
                default -> sendSystem(conn, "error", "Unknown message type");
            }
        } catch (Exception e) {
            sendSystem(conn, "error", "Invalid JSON or server error: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket bridge started on port " + getPort());
        ChatServer.addRoomBroadcastListener(this);
    }

    private void sendSystem(WebSocket conn, String event, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "system");
        o.addProperty("event", event);
        o.addProperty("text", text);
        conn.send(gson.toJson(o));
    }

    /* -- Handlers -- */

    private void handleRegister(WebSocket conn, JsonObject obj) {
        String username = obj.get("username").getAsString();
        String password = obj.get("password").getAsString();
        boolean ok = UserManager.register(username, password);
        if (ok) sendSystem(conn, "registered", "Registration successful");
        else sendSystem(conn, "registered", "Username already exists");
    }

    private void handleLogin(WebSocket conn, JsonObject obj) {
        String username = obj.get("username").getAsString();
        String password = obj.get("password").getAsString();
        boolean ok = UserManager.login(username, password);
        if (ok) {
            wsToUser.put(conn, username);
            userToWs.put(username, conn);
            sendSystem(conn, "login", "Login successful");
        } else {
            sendSystem(conn, "login", "Invalid credentials");
        }
    }

    private void handleJoin(WebSocket conn, JsonObject obj) {
        String username = wsToUser.get(conn);
        if (username == null) {
            sendSystem(conn, "error", "Not logged in");
            return;
        }
        String room = obj.get("room").getAsString();

        String oldRoom = userRoom.get(username);
        if (oldRoom != null && !oldRoom.equals(room)) {
            ChatServer.leaveRoom(username, oldRoom);
            RoomManager.removeUserFromRoom(username, oldRoom);
        }

        RoomManager.addUserToRoom(username, room);
        ChatServer.joinRoom(username, room);
        userRoom.put(username, room);

        List<String> history = MessageManager.getLastMessages(room, 20);
        JsonObject res = new JsonObject();
        res.addProperty("type", "history");
        res.add("lines", gson.toJsonTree(history));
        conn.send(gson.toJson(res));

        sendSystem(conn, "joined", "Joined room " + room);
    }

    private void handleLeave(WebSocket conn, JsonObject obj) {
        String username = wsToUser.get(conn);
        if (username == null) {
            sendSystem(conn, "error", "Not logged in");
            return;
        }
        String room = userRoom.remove(username);
        if (room != null) {
            ChatServer.leaveRoom(username, room);
            RoomManager.removeUserFromRoom(username, room);
            sendSystem(conn, "left", "Left room " + room);
        } else {
            sendSystem(conn, "error", "You were not in a room");
        }
    }

    private void handleMessage(WebSocket conn, JsonObject obj) {
        String username = wsToUser.get(conn);
        if (username == null) {
            sendSystem(conn, "error", "Not logged in");
            return;
        }
        String room = userRoom.get(username);
        if (room == null) {
            sendSystem(conn, "error", "You are not logged in");
            return;
        }
        String text = obj.get("text").getAsString();

        MessageManager.saveMessage(room, username, text);
        ChatServer.broadcastRoom(room, username, text);

        for (Map.Entry<String, WebSocket> e : userToWs.entrySet()) {
            if (room.equals(userRoom.get(e.getKey()))) {
                JsonObject msg = new JsonObject();
                msg.addProperty("type", "message");
                msg.addProperty("room", room);
                msg.addProperty("username", username);
                msg.addProperty("text", text);
                msg.addProperty("timestamp", new Date().toString());
                e.getValue().send(gson.toJson(msg));
            }
        }
    }

    private void handleRooms(WebSocket conn) {
        List<String> rooms = RoomManager.getAllRooms();
        JsonObject res = new JsonObject();
        res.addProperty("type", "rooms");
        res.add("rooms", gson.toJsonTree(rooms));
        conn.send(gson.toJson(res));
    }

    private void handleWho(WebSocket conn) {
        String username = wsToUser.get(conn);
        if (username == null) {
            sendSystem(conn, "error", "Not logged in");
            return;
        }
        String room = userRoom.get(username);
        if (room == null) {
            sendSystem(conn, "error", "You are not in a room");
            return;
        }

        List<String> users = RoomManager.getUsersInRoom(room);
        JsonObject res = new JsonObject();
        res.addProperty("type", "who");
        res.addProperty("room", room);
        res.add("users", gson.toJsonTree(users));
        conn.send(gson.toJson(res));
    }

    // Implement RoomBroadcastListener interface
    @Override
    public void onRoomMessage(String roomName, String message, String sender) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "message");
        msg.addProperty("room", roomName);
        msg.addProperty("text", message);
        msg.addProperty("sender", sender);
        String json = gson.toJson(msg);

        for (Map.Entry<String, WebSocket> e : userToWs.entrySet()) {
            String user = e.getKey();
            if (roomName.equals(userRoom.get(user))) {
                e.getValue().send(json);
            }
        }
    }
}
