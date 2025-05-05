package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatRoom;
import com.spshpau.chatservice.repositories.ChatRoomRepository;
import com.spshpau.chatservice.services.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatRoomServiceImpl implements ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    @Override
    public Optional<UUID> getChatRoomId (
            UUID senderId,
            UUID recipientId,
            boolean createNewRoomIfNotExists
    ) {
        return chatRoomRepository.findBySenderIdAndRecipientId(senderId, recipientId)
                .map(ChatRoom::getId)
                .or(() -> {
                    if (createNewRoomIfNotExists) {
                        var chatId = createChatId(senderId, recipientId);
                        return Optional.of(chatId);
                    }
                    return Optional.empty();
                });
    }

    private UUID createChatId(UUID senderId, UUID recipientId) {
        String combinedString = senderId.toString() + "|" + recipientId.toString();
        byte[] bytes = combinedString.getBytes(StandardCharsets.UTF_8);
        UUID chatId = UUID.nameUUIDFromBytes(bytes);

        // ToDo - Maybe explicitly specify id?
        ChatRoom senderRecipient = ChatRoom.builder()
                .chatId(chatId)
                .senderId(senderId)
                .recipientId(recipientId)
                .build();

        ChatRoom recipientSender = ChatRoom.builder()
                .chatId(chatId)
                .senderId(recipientId)
                .recipientId(senderId)
                .build();

        chatRoomRepository.save(senderRecipient);
        chatRoomRepository.save(recipientSender);
        return chatId;
    }
}
