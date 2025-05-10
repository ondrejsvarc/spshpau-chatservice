package com.spshpau.chatservice.repositories;

import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.model.enums.MessageStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, UUID> {
    List<ChatMessage> findByChatId(UUID chatId);

    List<ChatMessage> findByChatIdAndRecipientIdAndStatus(UUID chatId, UUID recipientId, MessageStatus status);

    List<ChatMessage> findByChatIdAndRecipientIdAndStatusIn(UUID chatId, UUID recipientId, List<MessageStatus> statuses);

    List<ChatMessage> findByRecipientIdAndStatus(UUID recipientId, MessageStatus status);

    List<ChatMessage> findByRecipientIdAndStatusIn(UUID recipientId, List<MessageStatus> statuses);
}
