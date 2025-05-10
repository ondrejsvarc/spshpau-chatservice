package com.spshpau.chatservice.services;

import com.spshpau.chatservice.model.ChatMessage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChatMessageService {
    ChatMessage save(ChatMessage chatMessage);
    List<ChatMessage> findChatMessages(UUID senderId, UUID recipientId);

    List<ChatMessage> markMessagesAsDelivered(UUID chatId, UUID recipientIdOfMessages);
    List<ChatMessage> markMessagesAsRead(UUID chatId, UUID recipientIdOfMessages);

    List<ChatMessage> markSentMessagesToUserAsDelivered(UUID recipientUserId);
    Map<UUID, Long> getUnreadMessageCountsPerChatForUser(UUID recipientUserId);
}
