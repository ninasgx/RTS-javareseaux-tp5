import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * ChatMessage represents one protocol message.
 * On the wire, the format is:
 * [4-byte int length][JSON body bytes]
 *
 * JSON example:
 * {
 *   "type":"TEXT_MESSAGE",
 *   "version":"1.0",
 *   "timestamp":1710000000000,
 *   "sender":"alice",
 *   "recipient":"bob",
 *   "room":"general",
 *   "content":"hello"
 * }
 *
 * This is a very simple and not fully general JSON handling,
 * but it is enough for the lab.
 */
public class ChatMessage {

    private MessageType type;
    private String version;
    private long timestamp;
    private String sender;
    private String recipient; // for private messages
    private String room;      // for room-based messages
    private String content;   // text of the message

    public ChatMessage(MessageType type) {
        this.type = type;
        this.version = "1.0";
        this.timestamp = System.currentTimeMillis();
    }

    // ----- Getters and setters -----

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // ----- JSON serialization (very simple) -----

    /**
     * Convert this message to a JSON string.
     * We build JSON manually to avoid external libraries.
     * WARNING: this is a simple implementation and assumes
     * fields do not contain quotes or commas.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        // type (required)
        sb.append("\"type\":\"").append(type.name()).append("\",");

        // version (required)
        sb.append("\"version\":\"").append(version).append("\",");

        // timestamp (required)
        sb.append("\"timestamp\":").append(timestamp).append(",");

        // optional fields
        if (sender != null) {
            sb.append("\"sender\":\"").append(sender).append("\",");
        }
        if (recipient != null) {
            sb.append("\"recipient\":\"").append(recipient).append("\",");
        }
        if (room != null) {
            sb.append("\"room\":\"").append(room).append("\",");
        }
        if (content != null) {
            sb.append("\"content\":\"").append(content).append("\",");
        }

        // remove trailing comma if present
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.deleteCharAt(sb.length() - 1);
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Parse a ChatMessage from a JSON string produced by toJson().
     * This is a simple parser that splits by commas and colons.
     */
    public static ChatMessage fromJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json is null");
        }

        json = json.trim();
        if (json.startsWith("{")) {
            json = json.substring(1);
        }
        if (json.endsWith("}")) {
            json = json.substring(0, json.length() - 1);
        }

        // now json is like: "key":"value","key2":"value2",...
        String[] pairs = json.split(",");
        MessageType type = null;
        String version = "1.0";
        long timestamp = System.currentTimeMillis();
        String sender = null;
        String recipient = null;
        String room = null;
        String content = null;

        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;

            String key = kv[0].trim();
            String value = kv[1].trim();

            // remove quotes around key
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            // remove quotes around value if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            switch (key) {
                case "type":
                    type = MessageType.valueOf(value);
                    break;
                case "version":
                    version = value;
                    break;
                case "timestamp":
                    try {
                        timestamp = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        timestamp = System.currentTimeMillis();
                    }
                    break;
                case "sender":
                    sender = value;
                    break;
                case "recipient":
                    recipient = value;
                    break;
                case "room":
                    room = value;
                    break;
                case "content":
                    content = value;
                    break;
                default:
                    // ignore unknown fields
                    break;
            }
        }

        if (type == null) {
            throw new IllegalArgumentException("Missing message type in JSON: " + json);
        }

        ChatMessage msg = new ChatMessage(type);
        msg.setVersion(version);
        msg.setTimestamp(timestamp);
        msg.setSender(sender);
        msg.setRecipient(recipient);
        msg.setRoom(room);
        msg.setContent(content);
        return msg;
    }

    // ----- Binary framing: [length][json bytes] -----

    /**
     * Serialize to bytes: [4-byte length][json bytes].
     */
    public byte[] toBytes() {
        String json = toJson();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(4 + jsonBytes.length);
        buffer.putInt(jsonBytes.length);
        buffer.put(jsonBytes);
        return buffer.array();
    }

    /**
     * Write this message to a DataOutputStream using the
     * [length][json bytes] format.
     */
    public void writeTo(DataOutputStream out) throws IOException {
        String json = toJson();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        out.writeInt(jsonBytes.length);
        out.write(jsonBytes);
        out.flush();
    }

    /**
     * Read one ChatMessage from a DataInputStream using the
     * [length][json bytes] format.
     */
    public static ChatMessage readFrom(DataInputStream in) throws IOException {
        // read length
        int len;
        try {
            len = in.readInt();
        } catch (IOException e) {
            // connection probably closed
            throw e;
        }

        if (len <= 0) {
            throw new IOException("Invalid message length: " + len);
        }

        byte[] jsonBytes = new byte[len];
        in.readFully(jsonBytes);

        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return fromJson(json);
    }
}
