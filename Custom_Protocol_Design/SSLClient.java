import java.io.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class SSLClient {

    private SSLSocket socket;
    private String host;
    private int port;
    private boolean trustAllCerts;

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    // create SSLContext that trusts all certificates
    private SSLContext createTrustAllSSLContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    public void connect() throws Exception {
        SSLContext ctx;
        if (trustAllCerts) {
            ctx = createTrustAllSSLContext();
        } else {
            ctx = SSLContext.getDefault(); // 
        }

        SSLSocketFactory factory = ctx.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);

        // TLS handshake
        socket.startHandshake();
        System.out.println("Connected to server. Cipher suite: " +
                socket.getSession().getCipherSuite());
    }

    public void sendMessage(String message) throws IOException {
        PrintWriter out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream()), true);
        out.println(message);
    }

    public String receiveResponse() throws IOException {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        return in.readLine();
    }

    public void disconnect() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    // 
    public static void main(String[] args) throws Exception {
        // 
        SSLClient client = new SSLClient("localhost", 8443, true);
        client.connect();

        BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Connected to server. Type messages to send (type 'quit' to exit):");
        while (true) {
            String line = console.readLine();
            if (line == null) break;
            if (line.equalsIgnoreCase("quit")) break;

            client.sendMessage(line);
            String resp = client.receiveResponse();
            System.out.println("Server Reply: " + resp);
        }

        client.disconnect();
        System.out.println("connection closed.");
    }
}
