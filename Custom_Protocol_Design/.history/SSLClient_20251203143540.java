import java.io.*;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class SSLClient {

    private SSLSocket socket;
    private String host;
    private int port;
    private boolean trustAllCerts; // 只在实验/测试时用

    public SSLClient(String host, int port, boolean trustAllCerts) {
        this.host = host;
        this.port = port;
        this.trustAllCerts = trustAllCerts;
    }

    // 创建“信任所有证书”的 SSLContext（因为我们用的是自签名证书）
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
            ctx = SSLContext.getDefault(); // 真·生产环境才用这个
        }

        SSLSocketFactory factory = ctx.getSocketFactory();
        socket = (SSLSocket) factory.createSocket(host, port);

        // 做 TLS 握手
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

    // 测试用 main：在命令行里跟服务器说话
    public static void main(String[] args) throws Exception {
        // 服务器在本机 8443 端口，trustAllCerts = true（因为是自签名证书）
        SSLClient client = new SSLClient("localhost", 8443, true);
        client.connect();

        BufferedReader console =
                new BufferedReader(new InputStreamReader(System.in));

        System.out.println("已连接到服务器，输入内容按回车发送，输入 quit 退出：");
        while (true) {
            String line = console.readLine();
            if (line == null) break;
            if (line.equalsIgnoreCase("quit")) break;

            client.sendMessage(line);
            String resp = client.receiveResponse();
            System.out.println("服务器回复: " + resp);
        }

        client.disconnect();
        System.out.println("连接已关闭。");
    }
}
