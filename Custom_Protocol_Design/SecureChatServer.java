import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.util.*;
import javax.net.ssl.*;

/**
 * SecureChatServer
 * - TLS server using server.jks keystore.
 * - Supports:
 *   * LOGIN_REQUEST / LOGIN_RESPONSE
 *   * JOIN_ROOM_REQUEST / JOIN_ROOM_RESPONSE
 *   * TEXT_MESSAGE  (room-based broadcast)
 *   * PRIVATE_MESSAGE (direct user-to-user)
 *   * ERROR_RESPONSE
 */
public class SecureChatServer {

    private final int port;
    private SSLServerSocket serverSocket;
    private volatile boolean running = true;

    // username -> ClientHandler
    private final Map<String, ClientHandler> clients =
            Collections.synchronizedMap(new HashMap<>());

    // roomName -> set of ClientHandler
    private final Map<String, Set<ClientHandler>> rooms =
            Collections.synchronizedMap(new HashMap<>());

    // default room name
    private static final String DEFAULT_ROOM = "lobby";

    public SecureChatServer(int port, String keystorePath, String keystorePassword) throws Exception {
        this.port = port;
        SSLContext ctx = createSSLContext(keystorePath, keystorePassword);
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        this.serverSocket = (SSLServerSocket) factory.createServerSocket(port);
        System.out.println("SecureChatServer listening on port " + port);
    }

    private SSLContext createSSLContext(String keystorePath, String keystorePassword) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12"); // server.jks is PKCS12
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

    private void addToRoom(String room, ClientHandler handler) {
        synchronized (rooms) {
            rooms.computeIfAbsent(room, r -> Collections.synchronizedSet(new HashSet<>()));
            rooms.get(room).add(handler);
        }
    }

    private void removeFromRoom(String room, ClientHandler handler) {
        synchronized (rooms) {
            Set<ClientHandler> set = rooms.get(room);
            if (set != null) {
                set.remove(handler);
                if (set.isEmpty()) {
                    rooms.remove(room);
                }
            }
        }
    }

    private void broadcastToRoom(String room, ChatMessage msg) {
        Set<ClientHandler> targets;
        synchronized (rooms) {
            targets = rooms.get(room) != null
                    ? new HashSet<>(rooms.get(room))
                    : Collections.emptySet();
        }
        for (ClientHandler handler : targets) {
            try {
                handler.send(msg);
            } catch (IOException e) {
                System.out.println("Failed to send to " + handler.getUsername() + ": " + e.getMessage());
            }
        }
    }

    private void removeClient(ClientHandler handler) {
        String username = handler.getUsername();
        String room = handler.getCurrentRoom();

        if (room != null) {
            removeFromRoom(room, handler);
        }

        if (username != null) {
            synchronized (clients) {
                clients.remove(username);
            }
            System.out.println("User logged out: " + username);

            ChatMessage notice = new ChatMessage(MessageType.TEXT_MESSAGE);
            notice.setSender("SERVER");
            notice.setRoom(room != null ? room : DEFAULT_ROOM);
            notice.setContent(username + " left the chat");
            broadcastToRoom(notice.getRoom(), notice);
        }
    }

    private class ClientHandler implements Runnable {
        private final SSLSocket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;
        private String currentRoom;

        ClientHandler(SSLSocket socket) {
            this.socket = socket;
            this.currentRoom = null;
        }

        String getUsername() {
            return username;
        }

        String getCurrentRoom() {
            return currentRoom;
        }

        @Override
        public void run() {
            try {
                socket.startHandshake();
                System.out.println("Handshake done with " + socket.getInetAddress());

                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // initial room join to DEFAULT_ROOM after successful login

                while (true) {
                    ChatMessage msg = ChatMessage.readFrom(in);
                    handleMessage(msg);
                }
            } catch (EOFException eof) {
                String nameOrAddr = (username != null) ? username : socket.getInetAddress().toString();
                System.out.println("Client disconnected: " + nameOrAddr);
            } catch (IOException e) {
                System.out.println("Client IO error: " + e.getMessage());
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
                case JOIN_ROOM_REQUEST:
                    handleJoinRoom(msg);
                    break;
                case TEXT_MESSAGE:
                    handleTextMessage(msg);
                    break;
                case PRIVATE_MESSAGE:
                    handlePrivateMessage(msg);
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

            // join default room
            this.currentRoom = DEFAULT_ROOM;
            addToRoom(DEFAULT_ROOM, this);

            ChatMessage resp = new ChatMessage(MessageType.LOGIN_RESPONSE);
            resp.setContent("OK");
            send(resp);

            ChatMessage notice = new ChatMessage(MessageType.TEXT_MESSAGE);
            notice.setSender("SERVER");
            notice.setRoom(DEFAULT_ROOM);
            notice.setContent(username + " joined the chat (room: " + DEFAULT_ROOM + ")");
            broadcastToRoom(DEFAULT_ROOM, notice);
        }

        private void handleJoinRoom(ChatMessage msg) throws IOException {
            if (username == null) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("You must LOGIN before joining rooms.");
                send(err);
                return;
            }

            String newRoom = msg.getRoom();
            if (newRoom == null || newRoom.isEmpty()) {
                ChatMessage resp = new ChatMessage(MessageType.JOIN_ROOM_RESPONSE);
                resp.setContent("ERROR: room name cannot be empty");
                send(resp);
                return;
            }

            // leave old room
            if (currentRoom != null) {
                removeFromRoom(currentRoom, this);
            }

            currentRoom = newRoom;
            addToRoom(currentRoom, this);

            System.out.println("User " + username + " joined room: " + currentRoom);

            ChatMessage resp = new ChatMessage(MessageType.JOIN_ROOM_RESPONSE);
            resp.setRoom(currentRoom);
            resp.setContent("OK");
            send(resp);

            ChatMessage notice = new ChatMessage(MessageType.TEXT_MESSAGE);
            notice.setSender("SERVER");
            notice.setRoom(currentRoom);
            notice.setContent(username + " joined the room");
            broadcastToRoom(currentRoom, notice);
        }

        private void handleTextMessage(ChatMessage msg) throws IOException {
            if (username == null) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("You must LOGIN before sending messages.");
                send(err);
                return;
            }

            String room = msg.getRoom();
            if (room == null || room.isEmpty()) {
                room = currentRoom;
            }
            if (room == null) {
                room = DEFAULT_ROOM;
                currentRoom = DEFAULT_ROOM;
                addToRoom(DEFAULT_ROOM, this);
            }

            ChatMessage outMsg = new ChatMessage(MessageType.TEXT_MESSAGE);
            outMsg.setSender(username);
            outMsg.setRoom(room);
            outMsg.setContent(msg.getContent());

            System.out.println("[" + room + "][" + username + "]: " + msg.getContent());

            broadcastToRoom(room, outMsg);
        }

        private void handlePrivateMessage(ChatMessage msg) throws IOException {
            if (username == null) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("You must LOGIN before sending private messages.");
                send(err);
                return;
            }

            String targetUser = msg.getRecipient();
            if (targetUser == null || targetUser.isEmpty()) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("Missing recipient for private message.");
                send(err);
                return;
            }

            ClientHandler target;
            synchronized (clients) {
                target = clients.get(targetUser);
            }
            if (target == null) {
                ChatMessage err = new ChatMessage(MessageType.ERROR_RESPONSE);
                err.setContent("User '" + targetUser + "' is not online.");
                send(err);
                return;
            }

            ChatMessage outMsg = new ChatMessage(MessageType.PRIVATE_MESSAGE);
            outMsg.setSender(username);
            outMsg.setRecipient(targetUser);
            outMsg.setContent(msg.getContent());

            // send to target
            target.send(outMsg);
            // optional: echo back to sender
            send(outMsg);
        }
    }

    public static void main(String[] args) throws Exception {
        String keystorePath = "server.jks";         // must be in current directory
        String keystorePassword = "password123";    // change if needed

        SecureChatServer server = new SecureChatServer(8443, keystorePath, keystorePassword);
        server.start();
    }
}
