import org.junit.jupiter.api.*;
import server.ChatServer;
import server.UserManager;
import testutils.TestClient;

import java.io.IOException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ChatServerIntegrationTest {

    private static final int PORT = 6969;
    private static ExecutorService serverThread;

    @BeforeAll
    static void startServer() {
        // Use in-memory db for testing
        UserManager.useDatabase("jdbc:sqlite::memory:");

        serverThread = Executors.newSingleThreadExecutor();
        serverThread.submit(() -> {
            ChatServer.main(null);
        });

        // Wait a moment for the server to start
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
    }

    @AfterAll
    static void stopServer() {
        serverThread.shutdownNow();
    }

    @BeforeEach
    void resetDb() {
        UserManager.resetDatabase();
        UserManager.seedTestUser("alice", "123");
    }

    // TODO: The tests work but there is a 'database table is locked' error with SQLite.
    @Test
    void testRegistration() throws IOException {
        try (TestClient client = new TestClient(PORT)) {
            assertNotNull(client.readLine()); // Welcome message
            client.send("register");
            client.readLine(); client.send("bob123");
            client.readLine(); client.send("password");
            String resp = client.readLine();
            assertTrue(resp.contains("Registration successful"));
        }
    }

    @Test
    void testLoginWithSeededUser() throws IOException {
        try (TestClient client = new TestClient(PORT)) {
            client.readLine();
            client.send("login");
            client.readLine();
            client.send("alice");
            client.readLine();
            client.send("123");
            String resp = client.readLine();
            assertTrue(resp.contains("Login successful"));
        }
    }

    @Test
    void testBroadcastBetweenTwoClients() throws IOException, InterruptedException {
        try (TestClient c1 = new TestClient(PORT); TestClient c2 = new TestClient(PORT)) {
            c1.readLine(); c1.send("login");
            c1.readLine(); c1.send("alice");
            c1.readLine(); c1.send("123");
            c1.readLine(); // login success msg

            c2.readLine(); c2.send("register");
            c2.readLine(); c2.send("bob");
            c2.readLine(); c2.send("12345");
            c2.readLine(); c2.send("login");
            c2.readLine(); c2.send("bob");
            c2.readLine(); c2.send("12345");
            c2.readLine(); // login success msg

            // broadcast message
            c1.send("Hello world!");
            c2.readLine(); // "bob joined the chat!"
            String msg = c2.readLine();
            assertTrue(msg.contains("alice: Hello world!"));
        }
    }
}

