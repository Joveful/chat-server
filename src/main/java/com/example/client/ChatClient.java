package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ChatClient {

    public static void main(String[] args) {
        String address = "localhost";
        int port = 6969;

        try (Socket socket = new Socket(address, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {

            new Thread(() -> {
                String response;
                try {
                    while ((response = in.readLine()) != null) {
                        System.out.println(response);
                    }
                } catch (IOException e) {
                    System.out.println("Connection closed.");
                }
            }).start();

            String input;
            while ((input = console.readLine()) != null) {
                out.println(input);
                if (input.equalsIgnoreCase("quit")) break;
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
