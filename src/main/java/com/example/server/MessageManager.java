package com.example.server.server;

import java.sql.*;
import java.util.*;

public class MessageManager {
    private static final String DB_URL = "jdbc:sqlite:chat.db";

    public static void saveMessage(String room, String user, String text) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement("""
                    INSERT INTO messages(room_name, username, text) VALUES(?,?,?)
            """);
            pstmt.setString(1, room);
            pstmt.setString(2, user);
            pstmt.setString(3, text);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<String> getLastMessages(String room, int limit) {
        List<String> messages = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement("""
                SELECT username, text, timestamp
                FROM messages
                WHERE room_name=?
                ORDER BY timestamp ASC
                LIMIT ?
            """);
            pstmt.setString(1, room);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String msg = "[" + rs.getString("timestamp") + "] "
                        + rs.getString("username") + ": "
                        + rs.getString("text");
                messages.add(msg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return messages;
    }
}
