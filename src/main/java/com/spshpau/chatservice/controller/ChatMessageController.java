package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.controller.dto.MarkAsReadPayloadDto;
import com.spshpau.chatservice.model.ChatMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;
import java.util.UUID;

public interface ChatMessageController {

    /**
     * Finds and retrieves chat messages between a sender and a recipient.
     *
     * @param senderId    The UUID of the sender.
     * @param recipientId The UUID of the recipient.
     * @return A ResponseEntity containing a list of ChatMessage objects.
     * Example Response (200 OK):
     * <pre>{@code
     * [
     * {
     * "id": "a1b2c3d4-e5f6-7890-1234-567890abcdef",
     * "chatId": "f0e1d2c3-b4a5-6789-0123-456789abcdef",
     * "senderId": "123e4567-e89b-12d3-a456-426614174000",
     * "recipientId": "123e4567-e89b-12d3-a456-426614174001",
     * "content": "Hello there!",
     * "status": "READ",
     * "sentAt": "2023-10-26T10:00:00Z",
     * "deliveredAt": "2023-10-26T10:00:05Z",
     * "readAt": "2023-10-26T10:01:00Z"
     * },
     * {
     * "id": "b2c3d4e5-f6a7-8901-2345-678901bcdef0",
     * "chatId": "f0e1d2c3-b4a5-6789-0123-456789abcdef",
     * "senderId": "123e4567-e89b-12d3-a456-426614174001",
     * "recipientId": "123e4567-e89b-12d3-a456-426614174000",
     * "content": "Hi!",
     * "status": "SENT",
     * "sentAt": "2023-10-26T10:02:00Z",
     * "deliveredAt": null,
     * "readAt": null
     * }
     * ]
     * }</pre>
     */
    ResponseEntity<List<ChatMessage>> findChatMessages (UUID senderId, UUID recipientId);

    /**
     * Marks messages within a specific chat as read by the recipient.
     * The recipient is identified from the STOMP session.
     * This is a WebSocket message mapping (@MessageMapping).
     * It sends a {@link com.spshpau.chatservice.controller.notifications.MessageStatusUpdateNotification}
     * to the sender via a user-specific queue ("/queue/status-updates").
     *
     * @param payload        The payload containing the chatId.
     * Example {@link com.spshpau.chatservice.controller.dto.MarkAsReadPayloadDto}:
     * <pre>{@code
     * {
     * "chatId": "f0e1d2c3-b4a5-6789-0123-456789abcdef"
     * }
     * }</pre>
     * @param headerAccessor The STOMP message header accessor, used to retrieve the authenticated user.
     */
    void markMessagesAsReadByRecipient(MarkAsReadPayloadDto payload, SimpMessageHeaderAccessor headerAccessor);

    /**
     * Processes an incoming chat message sent via WebSocket.
     * This method is mapped to a message broker destination (e.g., "/app/chat").
     * After saving the message, it sends a {@link com.spshpau.chatservice.controller.notifications.ChatNotification}
     * to the recipient via a user-specific queue ("/queue/messages").
     *
     * @param chatMessage The ChatMessage object to be processed.
     * Example {@link com.spshpau.chatservice.model.ChatMessage} payload (fields like id, chatId, status, sentAt are usually set by the server):
     * <pre>{@code
     * {
     * "senderId": "123e4567-e89b-12d3-a456-426614174000",
     * "recipientId": "123e4567-e89b-12d3-a456-426614174001",
     * "content": "This is a new message!"
     * }
     * }</pre>
     */
    void processMessage (ChatMessage chatMessage);
}
