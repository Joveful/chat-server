package com.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private String currentRoom;


    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            out.println("Welcome! Type 'login' or 'register'.");
            username = handleAuth();
            ChatServer.addUser(username, this);

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("quit")) break;

                if (message.startsWith("/")) {
                    handleCommand(message);
                } else {
                    if (currentRoom == null) {
                        out.println("You are not in a room. Use /join <room> to join one");
                    } else {
                        ChatServer.broadcastRoom(currentRoom, username + ": " + message, username);
                        ChatServer.logMessage(message, this);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Error with " + username + ": " + e.getMessage());
        } finally {
            if (username != null) ChatServer.removeUser(username);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String handleAuth() throws IOException {
        while (true) {
            String choice = in.readLine();
            if (choice.equals("login")) {
                out.println("Enter username:");
                String user = in.readLine();
                out.println("Enter password:");
                String password = in.readLine();

                if (UserManager.login(user, password)) {
                    out.println("Login successful!");
                    return user;
                } else {
                    out.println("Invalid credentials. Type 'login' to try again.");
                }
            } else if (choice.equals("register")) {
                out.println("Choose username:");
                String user = in.readLine();
                out.println("Choose password:");
                String password = in.readLine();
                if (UserManager.register(user, password)) {
                    out.println("Registration successful! Type 'login' to log in.");
                } else {
                    out.println("Username already exists. Type 'register' to try again.");
                }
            } else {
                out.println("Type 'login' or 'register' to continue.");
            }
        }
    }

    private void handleCommand(String cmd) {
        try {
            if (cmd.startsWith("/join ")) {
                String room = cmd.split(" ", 2)[1];
                if (currentRoom != null) ChatServer.leaveRoom(username, currentRoom);
                currentRoom = room;
                ChatServer.joinRoom(username, room);
                out.println("Joined room: " + room);

                out.println("Last messages:");
                List<String> history = MessageManager.getLastMessages(room, 20);
                if (history.isEmpty()) {
                    out.println("(No message history)");
                } else {
                    for (String msg : history) out.println(msg);
                }
            } else if (cmd.equalsIgnoreCase("/leave")) {
                if (currentRoom != null) {
                    ChatServer.leaveRoom(username, currentRoom);
                    out.println("Left room: " + currentRoom);
                    currentRoom = null;
                } else {
                    out.println("You are not in any room.");
                }
            } else if (cmd.equalsIgnoreCase("/rooms")) {
                out.println("Available rooms: " + ChatServer.getRooms());
            } else if (cmd.equalsIgnoreCase("/who")) {
                if (currentRoom == null) out.println("You are not in a room.");
                else out.println("Users in " + currentRoom + ": " + ChatServer.getRoomMembers(currentRoom));
            } else if (cmd.startsWith("/pm")) {
                String[] parts = cmd.split(" ", 3);
                if (parts.length < 3) {
                    out.println("Usage: /pm <user> <message>");
                } else {
                    String target = parts[1];
                    String msg = parts[2];
                    ChatServer.sendPrivate(target, "[PM from " + username + "]: " + msg);
                    out.println("[PM to " + target + "]: " + msg);
                }
            } else {
                out.println("Unknown command.");
            }
        } catch (Exception e) {
            out.println("Command error: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getName() {
        return this.username;
    }
}
