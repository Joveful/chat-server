package com.example.server.server;

import java.sql.*;
import java.util.*;

public class RoomManager {

    private static final String DB_URL = "jdbc:sqlite:chat.db";

    public static void init() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS rooms (
                    name TEXT PRIMARY KEY,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS room_members (
                    username TEXT,
                    room_name TEXT,
                    joined_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (username, room_name)
                )
            """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    room_name TEXT NOT NULL,
                    username TEXT NOT NULL,
                    text TEXT NOT NULL,
                    timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(room_name) REFERENCES rooms(name),
                    FOREIGN KEY(username) REFERENCES users(username)
                )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void createRoomIfNotExists(String roomName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement("INSERT OR IGNORE INTO rooms(name) VALUES(?)");
            pstmt.setString(1, roomName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void addUserToRoom(String username, String roomName) {
        createRoomIfNotExists(roomName);
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO room_members(username, room_name) VALUES(?, ?)"
            );
            pstmt.setString(1, username);
            pstmt.setString(2, roomName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void removeUserFromRoom(String username, String roomName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM room_members WHERE username=? AND room_name=?");
            ps.setString(1, username);
            ps.setString(2, roomName);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized List<String> getAllRooms() {
        List<String> rooms = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM rooms")) {
            while (rs.next()) rooms.add(rs.getString("name"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rooms;
    }

    public static synchronized List<String> getUsersInRoom(String roomName) {
        List<String> users = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement(
                    "SELECT username FROM room_members WHERE room_name=?"
            );
            pstmt.setString(1, roomName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) users.add(rs.getString("username"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public static synchronized void clearRoom(String roomName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM room_members WHERE room_name=?"
            );
            pstmt.setString(1, roomName);
            pstmt.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
