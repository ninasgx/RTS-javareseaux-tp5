import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.KeyStore;

public class SSLTCPServer {

    private int port;
    private SSLServerSocket serverSocket;
    private boolean isRunning;

    // constructor
    public SSLTCPServer(int port, String keystorePath, String password) throws Exception {
        this.port = port;

        // 1) Create SSLContext from keystore
        SSLContext sslContext = createSSLContext(keystorePath, password);

        // 2) Create SSLServerSocket from SSLContext
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        this.serverSocket = (SSLServerSocket) factory.createServerSocket(this.port);

        this.isRunning = true;
        System.out.println("SSL server listening on port " + this.port + "...");
    }

    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, password.toCharArray());
        }

        // server certificate + private key
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        return sslContext;
    }

    // server main loop
    public void launch() {
        System.out.println("Server started. Waiting for clients...");

        while (isRunning) {
            try {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();

                Thread t = new Thread(() -> handleClient(clientSocket));
                t.start();

            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Error accepting client: " + e.getMessage());
                }
            }
        }
    }

    // one client connection
    private void handleClient(SSLSocket clientSocket) {
        String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
        System.out.println("New client connected: " + clientInfo);

        try {
            // handshake
            clientSocket.startHandshake();
            System.out.println("SSL handshake successful with " + clientInfo);

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream())
            );
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream()),
                    true
            );

            out.println("Welcome to SSL echo server. Type /quit to exit.");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[" + clientInfo + "] " + line);

                if ("/quit".equalsIgnoreCase(line.trim())) {
                    out.println("Goodbye!");
                    break;
                }

                out.println("Echo: " + line);
            }

        } catch (IOException e) {
            System.err.println("Error during communication with client " + clientInfo + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client disconnected: " + clientInfo);
            } catch (IOException ignored) {}
        }
    }

    public void shutdown() {
        isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {}
        System.out.println("Server stopped.");
    }

    public static void main(String[] args) {
        int port = 8443;
        String keystorePath = "server.jks";
        String password = "password123";

        try {
            SSLTCPServer server = new SSLTCPServer(port, keystorePath, password);
            server.launch();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
