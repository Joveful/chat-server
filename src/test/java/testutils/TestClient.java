package testutils;

import java.io.*;
import java.net.Socket;

public class TestClient implements Closeable {

    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public TestClient(int port) throws IOException {
        this.socket = new Socket("localhost", port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void send(String msg) {
        out.println(msg);
    }

    public String readLine() throws IOException {
        return in.readLine();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
