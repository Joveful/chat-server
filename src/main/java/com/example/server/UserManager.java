package com.example.server.server;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.Base64;

public class UserManager {
    private static String DB_URL = "jdbc:sqlite:chat.db";
    private static Connection persistentConnection;

    static {
        initializeDatabase();
    }

    public static void useDatabase(String dbUrl) {
        DB_URL = dbUrl;
        initializeDatabase();
    }

    private static Connection getConnection() throws SQLException {
        if (DB_URL.equals("jdbc:sqlite::memory:")) {
            if (persistentConnection == null || persistentConnection.isClosed()) {
                persistentConnection = DriverManager.getConnection(DB_URL);
            }
            return persistentConnection;
        } else {
            return DriverManager.getConnection(DB_URL);
        }
    }

    private static void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username TEXT NOT NULL PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        salt TEXT NOT NULL
                        )
                    """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean register(String username, String password) {
        if (userExists(username)) return false;

        String salt = generateSalt();
        String hash = hashPassword(password, salt);

        String query = "INSERT INTO users(username, password_hash, salt) VALUES(?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hash);
            pstmt.setString(3, salt);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static synchronized boolean login(String username, String password) {
        String query = "SELECT password_hash, salt FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String storedSalt = rs.getString("salt");
                String hash = hashPassword(password, storedSalt);
                return storedHash.equals(hash);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean userExists(String username) {
        String sql = "SELECT username FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((password + salt).getBytes());
            byte[] bytes = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized void resetDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username TEXT NOT NULL PRIMARY KEY,
                        password_hash TEXT NOT NULL,
                        salt TEXT NOT NULL
                    )
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void seedTestUser(String username, String password) {
        register(username, password);
    }
}
