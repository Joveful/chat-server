import org.junit.jupiter.api.*;
import com.example.server.ChatServer;
import com.example.server.UserManager;
import testutils.ChatSession;
import testutils.TestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChatInteractionTest {

    private static final int PORT = 6969;

    @BeforeAll
    static void setupServer() {
        UserManager.useDatabase("jdbc:sqlite::memory:");
        UserManager.resetDatabase();
        UserManager.seedTestUser("alice", "123");
        UserManager.seedTestUser("bob", "456");

        new Thread(() -> ChatServer.main(null)).start();
        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
    }

    @BeforeEach
    void resetDb() {
        UserManager.resetDatabase();
        UserManager.seedTestUser("alice", "123");
        UserManager.seedTestUser("bob", "456");
    }

    @Test
    void testMessageExchangeBetweenUsers() throws IOException, InterruptedException {
        try (ChatSession session = new ChatSession()) {
            TestClient alice = session.connect(PORT);
            TestClient bob = session.connect(PORT);

            alice.readLine(); alice.send("login");
            alice.readLine(); alice.send("alice");
            alice.readLine(); alice.send("123");
            alice.readLine();

            bob.readLine(); bob.send("login");
            bob.readLine(); bob.send("bob");
            bob.readLine(); bob.send("456");
            bob.readLine();

            bob.readLine(); // bob joined message
            alice.send("Hey Bob!");
            String msgToBob = bob.readLine();
            assertTrue(msgToBob.contains("alice: Hey Bob!"), "Bob should see Alice’s message");

            alice.readLine();
            alice.readLine();
            alice.readLine(); // parse unwanted messages
            bob.send("Hey Alice, got your message!");
            String msgToAlice = alice.readLine();
            assertTrue(msgToAlice.contains("bob: Hey Alice, got your message!"), "Alice should see Bob’s reply");
        }
    }

    @Test
    void testMessageOrdering() throws IOException {
        try (ChatSession session = new ChatSession()) {
            TestClient c1 = session.connect(PORT);
            TestClient c2 = session.connect(PORT);

            // Register + login both quickly
            c1.readLine(); c1.send("login"); c1.readLine(); c1.send("alice"); c1.readLine(); c1.send("123"); c1.readLine();
            c2.readLine(); c2.send("login"); c2.readLine(); c2.send("bob");   c2.readLine(); c2.send("456"); c2.readLine();
            c2.readLine(); // bob joined message

            // Alice sends multiple messages quickly
            c1.send("msg1");
            c1.send("msg2");
            c1.send("msg3");

            String r1 = c2.readLine();
            String r2 = c2.readLine();
            String r3 = c2.readLine();

            assertTrue(r1.contains("msg1"));
            assertTrue(r2.contains("msg2"));
            assertTrue(r3.contains("msg3"));
        }
    }

    @Test
    void testJoinLeaveEvents() throws IOException {
        try (ChatSession session = new ChatSession()) {
            TestClient a = session.connect(PORT);
            TestClient b = session.connect(PORT);

            a.readLine(); b.readLine();
            a.send("register"); a.readLine(); a.send("john"); a.readLine(); a.send("111"); a.readLine();
            b.send("register"); b.readLine(); b.send("mary"); b.readLine(); b.send("222"); b.readLine();

            // Login both
            a.send("login"); a.readLine(); a.send("john"); a.readLine(); a.send("111"); a.readLine();
            b.send("login"); b.readLine(); b.send("mary"); b.readLine(); b.send("222"); b.readLine();

            // One disconnects
            a.send("quit");

            // The other should eventually see a "left" message (depending on server implementation)
            String event = b.readLine();
            assertTrue(event.toLowerCase().contains("john"), "Should mention the user who left");
        }
    }
}
