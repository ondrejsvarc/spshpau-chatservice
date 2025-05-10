package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatRoom;
import com.spshpau.chatservice.repositories.ChatRoomRepository;
import com.spshpau.chatservice.services.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Optional<UUID> getChatRoomId (
            UUID senderId,
            UUID recipientId,
            boolean createNewRoomIfNotExists
    ) {
        log.info("Attempting to get ChatRoomId for senderId: {} and recipientId: {}. Create if not exists: {}",
                senderId, recipientId, createNewRoomIfNotExists);

        Optional<ChatRoom> existingRoom = chatRoomRepository.findBySenderIdAndRecipientId(senderId, recipientId);

        if (existingRoom.isPresent()) {
            UUID chatId = existingRoom.get().getChatId();
            log.debug("Found existing ChatRoom with chatId: {} for senderId: {} and recipientId: {}",
                    chatId, senderId, recipientId);
            return Optional.of(chatId);
        } else {
            log.debug("No existing ChatRoom found for senderId: {} and recipientId: {}.", senderId, recipientId);
            if (createNewRoomIfNotExists) {
                log.info("createNewRoomIfNotExists is true. Proceeding to create new chat room entries.");
                var chatId = createChatRoomEntries(senderId, recipientId);
                return Optional.of(chatId);
            }
            log.info("createNewRoomIfNotExists is false. Returning empty Optional for ChatRoomId.");
            return Optional.empty();
        }
    }

    private UUID createChatRoomEntries(UUID senderId, UUID recipientId) {
        log.debug("Creating chat room entries for senderId: {} and recipientId: {}", senderId, recipientId);
        String combinedString;
        if (senderId.compareTo(recipientId) < 0) {
            combinedString = senderId.toString() + "|" + recipientId.toString();
        } else {
            combinedString = recipientId.toString() + "|" + senderId.toString();
        }
        byte[] bytes = combinedString.getBytes(StandardCharsets.UTF_8);
        UUID chatId = UUID.nameUUIDFromBytes(bytes);
        log.debug("Generated deterministic chatId: {} from combined string: {}", chatId, combinedString);

        UUID senderRecipientDocId = UUID.randomUUID();
        ChatRoom senderRecipient = ChatRoom.builder()
                .id(senderRecipientDocId)
                .chatId(chatId)
                .senderId(senderId)
                .recipientId(recipientId)
                .build();

        UUID recipientSenderDocId = UUID.randomUUID();
        ChatRoom recipientSender = ChatRoom.builder()
                .id(recipientSenderDocId)
                .chatId(chatId)
                .senderId(recipientId)
                .recipientId(senderId)
                .build();

        chatRoomRepository.save(senderRecipient);
        log.info("Saved ChatRoom entry: Id={}, ChatId={}, SenderId={}, RecipientId={}",
                senderRecipientDocId, chatId, senderId, recipientId);
        chatRoomRepository.save(recipientSender);
        log.info("Saved ChatRoom entry: Id={}, ChatId={}, SenderId={}, RecipientId={}",
                recipientSenderDocId, chatId, recipientId, senderId);

        return chatId;
    }
}
