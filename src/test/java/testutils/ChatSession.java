package testutils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChatSession implements AutoCloseable {
    private final List<TestClient> clients = new ArrayList<>();

    public TestClient connect(int port) throws IOException {
        TestClient client = new TestClient(port);
        clients.add(client);
        return client;
    }

    public void broadcast(String message) {
        for (TestClient c : clients) {
            c.send(message);
        }
    }

    public List<String> readAll() throws IOException {
        List<String> messages = new ArrayList<>();
        for (TestClient c : clients) {
            String msg = c.readLine();
            if (msg != null) messages.add(msg);
        }
        return messages;
    }

    @Override
    public void close() throws IOException {
        for (TestClient c : clients) c.close();
    }
}
