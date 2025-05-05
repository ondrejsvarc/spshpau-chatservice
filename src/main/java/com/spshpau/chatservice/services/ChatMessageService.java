package com.spshpau.chatservice.services;

import com.spshpau.chatservice.model.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatMessageService {
    ChatMessage save(ChatMessage chatMessage);
    List<ChatMessage> findChatMessages(UUID senderId, UUID recipientId);
}
