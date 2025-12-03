import java.io.*;
import java.security.KeyStore;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.*;

/**
 * SecureChatServer
 * - Uses TLS (SSL) with server.jks keystore.
 * - Accepts multiple clients.
 * - Supports:
 *   * LOGIN_REQUEST: client sends username in content.
 *   * TEXT_MESSAGE: broadcast message to all logged-in users.
 *
 * Protocol on the wire is based on ChatMessage:
 * [4-byte length][JSON body bytes]
 */
public class SecureChatServer {

    private final int port;
    private SSLServerSocket serverSocket;
    private volatile boolean running = true;

    // username -> ClientHandler
    private final Map<String, ClientHandler> clients =
            Collections.synchronizedMap(new HashMap<>());

    public SecureChatServer(int port, String keystorePath, String keystorePassword) throws Exception {
        this.port = port;
        SSLContext ctx = createSSLContext(keystorePath, keystorePassword);
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        this.serverSocket = (SSLServerSocket) factory.createServerSocket(port);
        System.out.println("SecureChatServer listening on port " + port);
    }

    private SSLContext createSSLContext(String keystorePath, String keystorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12"); // your keystore is PKCS12
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, keystorePassword.toCharArray());

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public void start() throws IOException {
        while (running) {
            SSLSocket socket = (SSLSocket) serverSocket.accept();
            System.out.println("Client connected: " + socket.getInetAddress());
            ClientHandler handler = new ClientHandler(socket);
            new Thread(handler).start();
        }
    }

    private void broadcast(ChatMessage msg) {
        synchronized (clients) {
            for (ClientHandler handler : clients.values()) {
                try {
                    handler.send(msg);
                } catch (IOException e) {
                    System.out.println("Failed to send to " + handler.getUsername() + ": " + e.getMessage());
                }
            }
        }
    }

    private void removeClient(ClientHandler handler) {
        String username = handler.getUsername();
        if (username != null) {
            synchronized (clients) {
                clients.remove(username);
            }
            System.out.println("User logged out: " + username);

            ChatMessage notice = new ChatMessage(MessageType.TEXT_MESSAGE);
            notice.setSender("SERVER");
            notice.setContent(username + " left the chat");
            broadcast(notice);
        }
    }

    private class ClientHandler implements Runnable {
        private final SSLSocket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;

        ClientHandler(SSLSocket socket) {
            this.socket = socket;
        }

        String getUsername() {
            return username;
        }

        @Override
        public void run() {
            try {
                socket.startHandshake();
                System.out.println("Handshake done with " + socket.getInetAddress());

                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                while (true) {
                    ChatMessage msg = ChatMessage.readFrom(in);
                    handleMessage(msg);
                }
            } catch (IOException e) {
                System.out.println("Client error: " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
                removeClient(this);
            }
        }

        void send(ChatMessage msg) throws IOException {
            msg.writeTo(out);
        }

        private void handleMessage(ChatMessage msg) throws IOException {
            if (msg == null) {
                return;
            }

            switch (msg.getType()) {
                case LOGIN_REQUEST:
                    handleLogin(msg);
                    break;
                case TEXT_MESSAGE:
                    handleTextMessage(msg);
                    break;
                default:
                    ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                    err.setContent("Unsupported message type: " + msg.getType());
                    send(err);
                    break;
            }
        }

        private void handleLogin(ChatMessage msg) throws IOException {
            String requestedUsername = msg.getContent();
            if (requestedUsername == null || requestedUsername.isEmpty()) {
                ChatMessage resp = new ChatMessage(MessageType.LOGIN_RESPONSE);
                resp.setContent("ERROR: username cannot be empty");
                send(resp);
                return;
            }

            synchronized (clients) {
                if (clients.containsKey(requestedUsername)) {
                    ChatMessage resp = new ChatMessage(MessageType.LOGIN_RESPONSE);
                    resp.setContent("ERROR: username already in use");
                    send(resp);
                    return;
                } else {
                    this.username = requestedUsername;
                    clients.put(requestedUsername, this);
                    System.out.println("User logged in: " + username);
                }
            }

            ChatMessage resp = new ChatMessage(MessageType.LOGIN_RESPONSE);
            resp.setContent("OK");
            send(resp);

            ChatMessage notice = new ChatMessage(MessageType.TEXT_MESSAGE);
            notice.setSender("SERVER");
            notice.setContent(username + " joined the chat");
            broadcast(notice);
        }

        private void handleTextMessage(ChatMessage msg) throws IOException {
            if (username == null) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("You must LOGIN before sending messages.");
                send(err);
                return;
            }

            ChatMessage outMsg = new ChatMessage(MessageType.TEXT_MESSAGE);
            outMsg.setSender(username);
            outMsg.setContent(msg.getContent());
            broadcast(outMsg);
        }
    }

    public static void main(String[] args) throws Exception {
        String keystorePath = "server.jks";         // must be in current directory
        String keystorePassword = "password123";    // CHANGE if you used a different password

        SecureChatServer server = new SecureChatServer(8443, keystorePath, keystorePassword);
        server.start();
    }
}
