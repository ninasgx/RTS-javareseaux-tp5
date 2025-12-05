
import java.io.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

/**
 * SecureChatClient - Connects to SecureChatServer over TLS. - Supports
 * commands: /login <name> : login /join <room> : join or create a room /msg
 * <text> : send message to current room /pm <user> <text> : send private
 * message /quit : exit
 */
public class SecureChatClient {

    private final String host;
    private final int port;
    private final boolean trustAll;

    private SSLSocket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running = true;

    private String currentRoom = null;

    public SecureChatClient(String host, int port, boolean trustAll) {
        this.host = host;
        this.port = port;
        this.trustAll = trustAll;
    }

    private SSLContext createTrustAllContext() throws Exception {
        TrustManager[] trustAllManagers = new TrustManager[]{
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
        SSLContext ctx = trustAll ? createTrustAllContext() : SSLContext.getDefault();
        SSLSocketFactory factory = ctx.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);
        socket.startHandshake();

        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());

        System.out.println("Connected to server: " + host + ":" + port);
        System.out.println("Use /login <username> <password> first.");

    }

    public void startConsole() throws IOException {
        Thread readerThread = new Thread(this::readLoop);
        readerThread.setDaemon(true);
        readerThread.start();

        BufferedReader console
                = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Commands:");
        System.out.println("  /login <username> <password>  - log in with a username");
        System.out.println("  /join <roomname>              - join or create a room");
        System.out.println("  /msg <username> <message>     - send private message to user");
        System.out.println("  /rooms                        - list rooms (not implemented)");
        System.out.println("  /users                        - list users (not implemented)");
        System.out.println("  /roommsg <text>               - send message to current room");
        System.out.println("  /quit                         - exit");

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
                // /login <username> <password>
                String rest = line.substring(7).trim();
                String[] parts = rest.split("\\s+", 2);
                if (parts.length < 1 || parts[0].isEmpty()) {
                    System.out.println("Usage: /login <username> <password>");
                    continue;
                }
                String username = parts[0];
                // password is accepted but not validated in this lab
                // String password = (parts.length > 1) ? parts[1] : "";

                ChatMessage msg = new ChatMessage(MessageType.LOGIN_REQUEST);
                msg.setContent(username);
                msg.writeTo(out);

            } else if (line.startsWith("/join ")) {
                // /join <roomname>
                String room = line.substring(6).trim();
                if (room.isEmpty()) {
                    System.out.println("Room name cannot be empty.");
                    continue;
                }
                ChatMessage msg = new ChatMessage(MessageType.JOIN_ROOM_REQUEST);
                msg.setRoom(room);
                msg.writeTo(out);

            } else if (line.startsWith("/msg ")) {
                // /msg <username> <message>  -> PRIVATE_MESSAGE
                String rest = line.substring(5).trim();
                int space = rest.indexOf(' ');
                if (space <= 0) {
                    System.out.println("Usage: /msg <username> <message>");
                    continue;
                }
                String targetUser = rest.substring(0, space).trim();
                String text = rest.substring(space + 1).trim();
                if (targetUser.isEmpty() || text.isEmpty()) {
                    System.out.println("Usage: /msg <username> <message>");
                    continue;
                }
                ChatMessage msg = new ChatMessage(MessageType.PRIVATE_MESSAGE);
                msg.setRecipient(targetUser);
                msg.setContent(text);
                msg.writeTo(out);

            } else if (line.startsWith("/roommsg ")) {
                // /roommsg <text>  -> room broadcast (TEXT_MESSAGE)
                String text = line.substring(9).trim();
                if (text.isEmpty()) {
                    System.out.println("Usage: /roommsg <text>");
                    continue;
                }
                if (currentRoom == null) {
                    System.out.println("You must /join a room first.");
                    continue;
                }
                ChatMessage msg = new ChatMessage(MessageType.TEXT_MESSAGE);
                msg.setContent(text);
                msg.setRoom(currentRoom);
                msg.writeTo(out);

            } else if (line.equals("/rooms")) {
                // not implemented, but command exists as required by the assignment
                System.out.println("Room listing is not implemented in this version.");

            } else if (line.equals("/users")) {
                // not implemented, but command exists as required by the assignment
                System.out.println("User listing is not implemented in this version.");

            } else if (line.equals("/quit")) {
                System.out.println("Closing connection...");
                running = false;
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                break;

            } else {
                System.out.println("Unknown command. Use /login, /join, /msg, /rooms, /users, /roommsg or /quit.");
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
            case JOIN_ROOM_RESPONSE:
                if ("OK".equals(msg.getContent())) {
                    currentRoom = msg.getRoom();
                    System.out.println("[ROOM] joined room: " + currentRoom);
                } else {
                    System.out.println("[ROOM] join failed: " + msg.getContent());
                }
                break;
            case TEXT_MESSAGE:
                String room = msg.getRoom();
                String sender = msg.getSender() != null ? msg.getSender() : "UNKNOWN";
                if (room != null) {
                    System.out.println("[" + room + "][" + sender + "]: " + msg.getContent());
                } else {
                    System.out.println("[" + sender + "]: " + msg.getContent());
                }
                break;
            case PRIVATE_MESSAGE:
                String from = msg.getSender() != null ? msg.getSender() : "UNKNOWN";
                System.out.println("[PM from " + from + "]: " + msg.getContent());
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
