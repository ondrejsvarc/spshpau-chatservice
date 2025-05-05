package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.model.ChatMessage;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

public interface ChatMessageController {
    ResponseEntity<List<ChatMessage>> findChatMessages (UUID senderId, UUID recipientId);
    void processMessage (ChatMessage chatMessage);
}
