package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.model.enums.MessageStatus;
import com.spshpau.chatservice.repositories.ChatMessageRepository;
import com.spshpau.chatservice.services.ChatMessageService;
import com.spshpau.chatservice.services.ChatRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageServiceImpl implements ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomService chatRoomService;

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        if (chatMessage.getId() == null) {
            chatMessage.setId(UUID.randomUUID());
        }
        chatMessage.setStatus(MessageStatus.SENT);
        if (chatMessage.getSentAt() == null) {
            chatMessage.setSentAt(Instant.now());
        }

        var chatId = chatRoomService.getChatRoomId(
                        chatMessage.getSenderId(),
                        chatMessage.getRecipientId(),
                        true
                )
                .orElseThrow(() -> new RuntimeException("Failed to get or create chat room for users "
                        + chatMessage.getSenderId() + " and " + chatMessage.getRecipientId()));

        chatMessage.setChatId(chatId);
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        log.info("Saved message {} with status SENT", savedMessage.getId());
        return savedMessage;
    }

    @Override
    public List<ChatMessage> findChatMessages(UUID senderId, UUID recipientId) {
        log.info("Finding chat messages from sender {} to recipient {}.", senderId, recipientId);
        var chatId = chatRoomService.getChatRoomId(senderId, recipientId, false);

        return chatId.map(chatMessageRepository::findByChatId).orElseGet(ArrayList::new);
    }

    @Override
    public List<ChatMessage> markMessagesAsDelivered(UUID chatId, UUID recipientIdOfMessages) {
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByChatIdAndRecipientIdAndStatus(
                chatId, recipientIdOfMessages, MessageStatus.SENT);

        List<ChatMessage> updatedMessages = new ArrayList<>();
        if (!messagesToUpdate.isEmpty()) {
            Instant deliveredTime = Instant.now();
            for (ChatMessage msg : messagesToUpdate) {
                msg.setStatus(MessageStatus.DELIVERED);
                msg.setDeliveredAt(deliveredTime);
                updatedMessages.add(chatMessageRepository.save(msg));
            }
            log.info("Marked {} messages in chat {} for recipient {} as DELIVERED", updatedMessages.size(), chatId, recipientIdOfMessages);
        }
        return updatedMessages;
    }

    @Override
    public List<ChatMessage> markMessagesAsRead(UUID chatId, UUID recipientIdOfMessages) {
        List<MessageStatus> statusesToMarkAsRead = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByChatIdAndRecipientIdAndStatusIn(
                chatId, recipientIdOfMessages, statusesToMarkAsRead);

        List<ChatMessage> updatedMessages = new ArrayList<>();
        if (!messagesToUpdate.isEmpty()) {
            Instant readTime = Instant.now();
            for (ChatMessage msg : messagesToUpdate) {
                if (msg.getStatus() == MessageStatus.SENT && msg.getDeliveredAt() == null) {
                    msg.setDeliveredAt(readTime);
                }
                msg.setStatus(MessageStatus.READ);
                msg.setReadAt(readTime);
                updatedMessages.add(chatMessageRepository.save(msg));
            }
            log.info("Marked {} messages in chat {} for recipient {} as READ", updatedMessages.size(), chatId, recipientIdOfMessages);
        }
        return updatedMessages;
    }

    @Override
    public List<ChatMessage> markSentMessagesToUserAsDelivered(UUID recipientUserId) {
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByRecipientIdAndStatus(recipientUserId, MessageStatus.SENT);
        List<ChatMessage> updatedMessages = new ArrayList<>();
        if (!messagesToUpdate.isEmpty()) {
            Instant deliveredTime = Instant.now();
            for (ChatMessage msg : messagesToUpdate) {
                msg.setStatus(MessageStatus.DELIVERED);
                msg.setDeliveredAt(deliveredTime);
                updatedMessages.add(chatMessageRepository.save(msg));
            }
            log.info("Marked {} messages for recipient {} across all chats as DELIVERED", updatedMessages.size(), recipientUserId);
        }
        return updatedMessages;
    }

    @Override
    public Map<UUID, Long> getUnreadMessageCountsPerChatForUser(UUID recipientUserId) {
        List<MessageStatus> unreadStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);
        List<ChatMessage> allUnreadMessagesForUser = chatMessageRepository.findByRecipientIdAndStatusIn(recipientUserId, unreadStatuses);

        return allUnreadMessagesForUser.stream()
                .collect(Collectors.groupingBy(ChatMessage::getChatId, Collectors.counting()));
    }
}
