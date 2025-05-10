package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.controller.dto.MarkAsReadPayloadDto;
import com.spshpau.chatservice.model.ChatMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;
import java.util.UUID;

public interface ChatMessageController {
    ResponseEntity<List<ChatMessage>> findChatMessages (UUID senderId, UUID recipientId);
    void markMessagesAsReadByRecipient(MarkAsReadPayloadDto payload, SimpMessageHeaderAccessor headerAccessor);
    void processMessage (ChatMessage chatMessage);
}
