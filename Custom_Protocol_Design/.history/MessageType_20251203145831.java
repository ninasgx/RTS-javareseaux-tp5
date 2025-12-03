public enum MessageType {
    // Client -> Server
    LOGIN_REQUEST,       // user wants to login
    JOIN_ROOM_REQUEST,   // user wants to join a room
    TEXT_MESSAGE,        // normal room message
    PRIVATE_MESSAGE,     // direct message to one user
    USER_LIST_REQUEST,   // client asks for list of users

    // Server -> Client
    LOGIN_RESPONSE,      // login result
    JOIN_ROOM_RESPONSE,  // join room result
    USER_LIST_RESPONSE,  // list of users
    ERROR_RESPONSE       // error information
}
