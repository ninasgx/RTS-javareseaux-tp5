import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * SSLClient for TP5
 *
 * - Connects to an SSL/TLS server (SSLTCPServer)
 * - Can run in:
 *   * testing mode: trustAllCerts = true (accept all certificates)
 *   * production mode: trustAllCerts = false (use default truststore)
 * - Sends user input to the server and prints the echo
 */
public class SSLClient {

    private SSLSocket socket;
    private String host;
    private int port;
    private boolean trustAllCerts;

    private BufferedReader in;
    private PrintWriter out;

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    // === Create SSLContext depending on mode ===
    private SSLContext createSSLContext() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        if (trustAllCerts) {
            // -------- TESTING MODE: accept all certificates (self-signed) --------
            TrustManager[] trustAllManagers = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        // no-op
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        // no-op
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
            };

            sslContext.init(null, trustAllManagers, new SecureRandom());
        } else {
            // -------- PRODUCTION MODE: use default trust store (CA certificates) --------
            sslContext.init(null, null, new SecureRandom());
        }

        return sslContext;
    }

    // === Connect and perform SSL handshake ===
    public void connect() throws Exception {
        SSLContext context = createSSLContext();
        SSLSocketFactory factory = context.getSocketFactory();

        System.out.println("Connecting to " + host + ":" + port + " ...");
        socket = (SSLSocket) factory.createSocket(host, port);

        // Optional: restrict protocols if needed
        // socket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});

        socket.startHandshake();
        System.out.println("SSL handshake successful.");

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);

        // Read server welcome message if any
        String welcome = in.readLine();
        if (welcome != null) {
            System.out.println("[Server] " + welcome);
        }
    }

    // === Send one message to server ===
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    // === Receive one line from server ===
    public String receiveResponse() throws IOException {
        if (in != null) {
            return in.readLine();
        }
        return null;
    }

    // === Disconnect cleanly ===
    public void disconnect() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        if (out != null) out.close();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}

        System.out.println("Disconnected from server.");
    }

    // === Simple CLI for testing ===
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8443;

        // true  = testing mode (trust all certs, OK for self-signed)
        // false = production mode (validate certificates)
        boolean trustAllCerts = true;

        SSLClient client = new SSLClient(host, port, trustAllCerts);

        try {
            client.connect();

            BufferedReader console =
                    new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print("> ");
                String line = console.readLine();
                if (line == null) break;

                client.sendMessage(line);

                String response = client.receiveResponse();
                if (response != null) {
                    System.out.println("[Server] " + response);
                }

                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.disconnect();
        }
    }
}
