package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.controller.dto.ChatSummaryDto;
import com.spshpau.chatservice.controller.dto.UserPayloadDto;
import com.spshpau.chatservice.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserController {

    /**
     * Adds a new user to the system or updates an existing user's presence.
     * This is a WebSocket message mapping (@MessageMapping) and typically sends to "/topic/presence".
     * The method returns a {@link com.spshpau.chatservice.model.User} object over WebSocket.
     *
     * @param payload The UserPayloadDto containing user details.
     * Example {@link com.spshpau.chatservice.controller.dto.UserPayloadDto}:
     * <pre>{@code
     * {
     * "userId": "123e4567-e89b-12d3-a456-426614174000",
     * "username": "john.doe",
     * "firstName": "John",
     * "lastName": "Doe"
     * }
     * }</pre>
     * @return The User object that was added or updated, sent over WebSocket.
     * Example returned {@link com.spshpau.chatservice.model.User} over WebSocket:
     * <pre>{@code
     * {
     * "id": "123e4567-e89b-12d3-a456-426614174000",
     * "username": "john.doe",
     * "firstName": "John",
     * "lastName": "Doe",
     * "status": "ONLINE"
     * }
     * }</pre>
     */
    User addUser(@Payload UserPayloadDto payload);

    /**
     * Disconnects a user from the system, typically marking them as offline.
     * This is a WebSocket message mapping (@MessageMapping) and typically sends to "/topic/presence".
     * The method returns a {@link com.spshpau.chatservice.model.User} object over WebSocket.
     *
     * @param payload The UserPayloadDto containing the userId of the user to disconnect.
     * Example {@link com.spshpau.chatservice.controller.dto.UserPayloadDto}:
     * <pre>{@code
     * {
     * "userId": "123e4567-e89b-12d3-a456-426614174000"
     * }
     * }</pre>
     * @return The User object that was disconnected, or null if not found, sent over WebSocket.
     * Example returned {@link com.spshpau.chatservice.model.User} over WebSocket:
     * <pre>{@code
     * {
     * "id": "123e4567-e89b-12d3-a456-426614174000",
     * "username": "john.doe",
     * "firstName": "John",
     * "lastName": "Doe",
     * "status": "OFFLINE"
     * }
     * }</pre>
     */
    User disconnect(@Payload UserPayloadDto payload);

    /**
     * Finds and retrieves a list of all currently connected (online) users.
     *
     * @return A ResponseEntity containing a list of User objects.
     * Example Response (200 OK):
     * <pre>{@code
     * [
     * {
     * "id": "123e4567-e89b-12d3-a456-426614174000",
     * "username": "john.doe",
     * "firstName": "John",
     * "lastName": "Doe",
     * "status": "ONLINE"
     * },
     * {
     * "id": "789a0123-b4c5-d6e7-f8a9-b0c1d2e3f4a5",
     * "username": "jane.smith",
     * "firstName": "Jane",
     * "lastName": "Smith",
     * "status": "ONLINE"
     * }
     * ]
     * }</pre>
     */
    ResponseEntity<List<User>> findConnectedUsers();

    /**
     * Retrieves a list of users with whom the currently authenticated user has active chats.
     * The authenticated user is identified from the provided JWT.
     *
     * @param jwt The JWT of the authenticated user.
     * @return A ResponseEntity containing a list of User objects representing chat partners.
     * Example Response (200 OK):
     * <pre>{@code
     * [
     * {
     * "id": "123e4567-e89b-12d3-a456-426614174001",
     * "username": "chat.partner1",
     * "firstName": "Chat",
     * "lastName": "PartnerOne",
     * "status": "ONLINE"
     * },
     * {
     * "id": "123e4567-e89b-12d3-a456-426614174002",
     * "username": "another.friend",
     * "firstName": "Another",
     * "lastName": "Friend",
     * "status": "OFFLINE"
     * }
     * ]
     * }</pre>
     */
    ResponseEntity<List<User>> getMyChats(Jwt jwt);

    /**
     * Retrieves a list of chat summaries for the currently authenticated user.
     * A chat summary includes the chat partner, chat ID, and the count of unread messages.
     * The authenticated user is identified from the provided JWT.
     *
     * @param jwt The JWT of the authenticated user.
     * @return A ResponseEntity containing a list of ChatSummaryDto objects.
     * Example Response (200 OK):
     * <pre>{@code
     * [
     * {
     * "chatPartner": {
     * "id": "123e4567-e89b-12d3-a456-426614174001",
     * "username": "chat.partner1",
     * "firstName": "Chat",
     * "lastName": "PartnerOne",
     * "status": "ONLINE"
     * },
     * "chatId": "f0e1d2c3-b4a5-6789-0123-456789abcdef",
     * "unreadCount": 3
     * },
     * {
     * "chatPartner": {
     * "id": "123e4567-e89b-12d3-a456-426614174002",
     * "username": "another.friend",
     * "firstName": "Another",
     * "lastName": "Friend",
     * "status": "OFFLINE"
     * },
     * "chatId": "a0b1c2d3-e4f5-6789-0123-abcdeffedcba",
     * "unreadCount": 0
     * }
     * ]
     * }</pre>
     */
    ResponseEntity<List<ChatSummaryDto>> getMyChatSummaries(Jwt jwt);
}
