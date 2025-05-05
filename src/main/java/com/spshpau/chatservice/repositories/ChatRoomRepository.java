package com.spshpau.chatservice.repositories;

import com.spshpau.chatservice.model.ChatRoom;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, UUID> {
    Optional<ChatRoom> findBySenderIdAndRecipientId(UUID senderId, UUID recipientId);
}
