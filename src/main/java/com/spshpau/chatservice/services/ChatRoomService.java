package com.spshpau.chatservice.services;

import com.spshpau.chatservice.model.ChatMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomService {
    Optional<UUID> getChatRoomId (UUID senderId, UUID recipientId, boolean createNewRoomIfNotExists);
}
