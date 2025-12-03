public enum MessageType {
    LOGIN_REQUEST,      // Client -> Server
    LOGIN_RESPONSE,     // Server -> Client

    JOIN_ROOM_REQUEST,
    JOIN_ROOM_RESPONSE,

    TEXT_MESSAGE,       // 房间里普通消息
    PRIVATE_MESSAGE,    // 私聊消息

    USER_LIST_REQUEST,
    USER_LIST_RESPONSE,

    ERROR_RESPONSE;
}
