public enum MessageType {
    // Client -> Server
    LOGIN_REQUEST,
    JOIN_ROOM_REQUEST,
    TEXT_MESSAGE,
    PRIVATE_MESSAGE,

    // Server -> Client
    LOGIN_RESPONSE,
    JOIN_ROOM_RESPONSE,
    ERROR_RESPONSE
}
