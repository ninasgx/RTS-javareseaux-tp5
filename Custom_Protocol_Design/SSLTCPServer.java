import java.io.*;
import java.security.KeyStore;
import javax.net.ssl.*;

public class SSLTCPServer {

    private int port;
    private SSLServerSocket serverSocket;
    private boolean isRunning;

    public SSLTCPServer(int port, String keystorePath, String password) throws Exception {
        this.port = port;

        SSLContext ctx = createSSLContext(keystorePath, password);
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        serverSocket = (SSLServerSocket) factory.createServerSocket(port);

        isRunning = true;
        System.out.println("SSL server listening on port " + port);
    }

    private SSLContext createSSLContext(String keystorePath, String password) throws Exception {
        // 1. load KeyStore
        KeyStore ks = KeyStore.getInstance("PKCS12"); // 
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            ks.load(fis, password.toCharArray());
        }

        // 2. initialize KeyManagerFactory
        KeyManagerFactory kmf =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        // 3. create SSLContext
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    public void launch() throws IOException {
        while (isRunning) {
            SSLSocket client = (SSLSocket) serverSocket.accept();
            System.out.println("Client connected: " + client.getInetAddress());

            new Thread(() -> handleClient(client)).start();
        }
    }

    private void handleClient(SSLSocket clientSocket) {
        try {
            clientSocket.startHandshake();
            System.out.println("Handshake done with " + clientSocket.getInetAddress());

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream()), true);

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received from client: " + line);
                out.println("Echo: " + line);
            }

        } catch (Exception e) {
            System.out.println("Client error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }

    public void shutdown() {
        isRunning = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {}
    }

    // entry point
    public static void main(String[] args) throws Exception {
        SSLTCPServer server = new SSLTCPServer(8443, "server.jks", "password123");
        server.launch();
    }
}
