package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.repositories.ChatMessageRepository;
import com.spshpau.chatservice.services.ChatMessageService;
import com.spshpau.chatservice.services.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomService chatRoomService;

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        chatMessage.setId(UUID.randomUUID());

        var chatId = chatRoomService.getChatRoomId(
                        chatMessage.getSenderId(),
                        chatMessage.getRecipientId(),
                        true
                )
                .orElseThrow(() -> new RuntimeException("Failed to get or create chat room for users "
                        + chatMessage.getSenderId() + " and " + chatMessage.getRecipientId()));

        chatMessage.setChatId(chatId);

        chatMessageRepository.save(chatMessage);
        return chatMessage;
    }

    @Override
    public List<ChatMessage> findChatMessages(UUID senderId, UUID recipientId) {
        var chatId = chatRoomService.getChatRoomId(senderId, recipientId, false);

        return chatId.map(chatMessageRepository::findByChatId).orElseGet(ArrayList::new);
    }
}
