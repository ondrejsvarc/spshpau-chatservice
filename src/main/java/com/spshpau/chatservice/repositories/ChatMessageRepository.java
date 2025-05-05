package com.spshpau.chatservice.repositories;

import com.spshpau.chatservice.model.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, UUID> {
    List<ChatMessage> findByChatId(UUID chatId);
}
