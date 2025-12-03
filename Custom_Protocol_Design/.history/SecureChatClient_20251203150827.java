import java.io.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/**
 * SecureChatClient
 * - Connects to SecureChatServer using TLS.
 * - Uses ChatMessage for protocol.
 * - Console commands:
 *   /login <name>
 *   /msg <text>
 *   /quit
 */
public class SecureChatClient {

    private final String host;
    private final int port;
    private final boolean trustAll;

    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running = true;

    public SecureChatClient(String host, int port, boolean trustAll) {
        this.host = host;
        this.port = port;
        this.trustAll = trustAll;
    }

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAllManagers = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // trust all
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // trust all
                    }
                }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAllManagers, new SecureRandom());
        return ctx;
    }

    public void connect() throws Exception {
        SSLContext ctx;
        if (trustAll) {
            ctx = createTrustAllContext();
        } else {
            ctx = SSLContext.getDefault();
        }

        SSLSocketFactory factory = ctx.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();

        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        System.out.println("Connected to server: " + host + ":" + port);
        System.out.println("You should /login <name> first.");
    }

    public void startConsole() throws IOException {
        Thread readerThread = new Thread(this::readLoop);
        readerThread.setDaemon(true);
        readerThread.start();

        BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Commands:");
        System.out.println("  /login <name>   - log in with a username");
        System.out.println("  /msg <text>     - send message to everyone");
        System.out.println("  /quit           - exit");

        while (running) {
            String line = console.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("/login ")) {
                String username = line.substring(7).trim();
                if (username.isEmpty()) {
                    System.out.println("Username cannot be empty.");
                    continue;
                }
                ChatMessage msg = new ChatMessage(MessageType.LOGIN_REQUEST);
                msg.setContent(username); // server uses content as requested username
                msg.writeTo(out);

            } else if (line.startsWith("/msg ")) {
                String text = line.substring(5).trim();
                if (text.isEmpty()) {
                    continue;
                }
                ChatMessage msg = new ChatMessage(MessageType.TEXT_MESSAGE);
                msg.setContent(text);
                msg.writeTo(out);

            } else if (line.equals("/quit")) {
                System.out.println("Closing connection...");
                running = false;
                try {
                    socket.close();
                } catch (IOException ignored) {}
                break;

            } else {
                System.out.println("Unknown command. Use /login, /msg or /quit.");
            }
        }
    }

    private void readLoop() {
        try {
            while (running) {
                ChatMessage msg = ChatMessage.readFrom(in);
                handleIncoming(msg);
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Connection closed by server: " + e.getMessage());
            }
        } finally {
            running = false;
        }
    }

    private void handleIncoming(ChatMessage msg) {
        if (msg == null) {
            return;
        }

        switch (msg.getType()) {
            case LOGIN_RESPONSE:
                System.out.println("[LOGIN] " + msg.getContent());
                break;
            case TEXT_MESSAGE:
                String from = msg.getSender();
                if (from == null) {
                    from = "UNKNOWN";
                }
                System.out.println("[" + from + "]: " + msg.getContent());
                break;
            case ERROR_RESPONSE:
                System.out.println("[ERROR] " + msg.getContent());
                break;
            default:
                System.out.println("[SERVER] " + msg.getType() + " " + msg.getContent());
                break;
        }
    }

    public static void main(String[] args) throws Exception {
        SecureChatClient client = new SecureChatClient("localhost", 8443, true);
        client.connect();
        client.startConsole();
        System.out.println("Client terminated.");
    }
}
