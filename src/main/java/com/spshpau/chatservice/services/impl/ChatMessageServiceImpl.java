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
        log.info("Attempting to save chat message from senderId: {} to recipientId: {}",
                chatMessage.getSenderId(), chatMessage.getRecipientId());
        if (chatMessage.getId() == null) {
            chatMessage.setId(UUID.randomUUID());
            log.debug("Generated new ID for chat message: {}", chatMessage.getId());
        }
        chatMessage.setStatus(MessageStatus.SENT);
        if (chatMessage.getSentAt() == null) {
            chatMessage.setSentAt(Instant.now());
            log.debug("Set sentAt timestamp for message ID {}: {}", chatMessage.getId(), chatMessage.getSentAt());
        }

        var chatId = chatRoomService.getChatRoomId(
                        chatMessage.getSenderId(),
                        chatMessage.getRecipientId(),
                        true
                )
                .orElseThrow(() -> {
                    log.error("Failed to get or create chat room for users {} and {}. Cannot save message.",
                            chatMessage.getSenderId(), chatMessage.getRecipientId());
                    return new RuntimeException("Failed to get or create chat room for users "
                            + chatMessage.getSenderId() + " and " + chatMessage.getRecipientId());
                });
        log.debug("Obtained chatId: {} for message from senderId: {} to recipientId: {}",
                chatId, chatMessage.getSenderId(), chatMessage.getRecipientId());

        chatMessage.setChatId(chatId);
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        log.info("Saved message {} with status SENT. ChatId: {}", savedMessage.getId(), savedMessage.getChatId());
        return savedMessage;
    }

    @Override
    public List<ChatMessage> findChatMessages(UUID senderId, UUID recipientId) {
        log.info("Finding chat messages between senderId: {} and recipientId: {}.", senderId, recipientId);
        var optChatId = chatRoomService.getChatRoomId(senderId, recipientId, false);

        if (optChatId.isEmpty()) {
            log.info("No chat room found between senderId: {} and recipientId: {}. Returning empty list.", senderId, recipientId);
            return new ArrayList<>();
        }
        UUID chatId = optChatId.get();
        log.debug("Found chatId: {} for senderId: {} and recipientId: {}. Fetching messages.", chatId, senderId, recipientId);
        List<ChatMessage> messages = chatMessageRepository.findByChatId(chatId);
        log.info("Found {} messages for chatId: {}", messages.size(), chatId);
        return messages;
    }

    @Override
    public List<ChatMessage> markMessagesAsDelivered(UUID chatId, UUID recipientIdOfMessages) {
        log.info("Attempting to mark messages as DELIVERED for chatId: {} and recipientId: {}", chatId, recipientIdOfMessages);
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByChatIdAndRecipientIdAndStatus(
                chatId, recipientIdOfMessages, MessageStatus.SENT);

        if (messagesToUpdate.isEmpty()) {
            log.info("No messages found with status SENT for chatId: {} and recipientId: {} to mark as DELIVERED.", chatId, recipientIdOfMessages);
            return Collections.emptyList();
        }

        List<ChatMessage> updatedMessages = new ArrayList<>();
        Instant deliveredTime = Instant.now();
        for (ChatMessage msg : messagesToUpdate) {
            msg.setStatus(MessageStatus.DELIVERED);
            msg.setDeliveredAt(deliveredTime);
            updatedMessages.add(chatMessageRepository.save(msg));
            log.debug("Marked messageId: {} as DELIVERED at {}", msg.getId(), deliveredTime);
        }
        log.info("Marked {} messages in chatId: {} for recipientId: {} as DELIVERED", updatedMessages.size(), chatId, recipientIdOfMessages);
        return updatedMessages;
    }

    @Override
    public List<ChatMessage> markMessagesAsRead(UUID chatId, UUID recipientIdOfMessages) {
        log.info("Attempting to mark messages as READ for chatId: {} and recipientId: {}", chatId, recipientIdOfMessages);
        List<MessageStatus> statusesToMarkAsRead = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByChatIdAndRecipientIdAndStatusIn(
                chatId, recipientIdOfMessages, statusesToMarkAsRead);

        if (messagesToUpdate.isEmpty()) {
            log.info("No messages found with status SENT or DELIVERED for chatId: {} and recipientId: {} to mark as READ.", chatId, recipientIdOfMessages);
            return Collections.emptyList();
        }

        List<ChatMessage> updatedMessages = new ArrayList<>();
        Instant readTime = Instant.now();
        for (ChatMessage msg : messagesToUpdate) {
            if (msg.getStatus() == MessageStatus.SENT && msg.getDeliveredAt() == null) {
                msg.setDeliveredAt(readTime);
                log.debug("MessageId: {} was SENT, also setting deliveredAt: {}", msg.getId(), readTime);
            }
            msg.setStatus(MessageStatus.READ);
            msg.setReadAt(readTime);
            updatedMessages.add(chatMessageRepository.save(msg));
            log.debug("Marked messageId: {} as READ at {}", msg.getId(), readTime);
        }
        log.info("Marked {} messages in chatId: {} for recipientId: {} as READ", updatedMessages.size(), chatId, recipientIdOfMessages);
        return updatedMessages;
    }

    @Override
    public List<ChatMessage> markSentMessagesToUserAsDelivered(UUID recipientUserId) {
        log.info("Attempting to mark all SENT messages to recipientUserId: {} as DELIVERED across all chats.", recipientUserId);
        List<ChatMessage> messagesToUpdate = chatMessageRepository.findByRecipientIdAndStatus(recipientUserId, MessageStatus.SENT);

        if (messagesToUpdate.isEmpty()) {
            log.info("No messages found with status SENT for recipientUserId: {} to mark as DELIVERED.", recipientUserId);
            return Collections.emptyList();
        }

        List<ChatMessage> updatedMessages = new ArrayList<>();
        Instant deliveredTime = Instant.now();
        for (ChatMessage msg : messagesToUpdate) {
            msg.setStatus(MessageStatus.DELIVERED);
            msg.setDeliveredAt(deliveredTime);
            updatedMessages.add(chatMessageRepository.save(msg));
            log.debug("Marked messageId: {} (chatId: {}) for recipientId: {} as DELIVERED at {}",
                    msg.getId(), msg.getChatId(), recipientUserId, deliveredTime);
        }
        log.info("Marked {} messages for recipientUserId: {} across all chats as DELIVERED", updatedMessages.size(), recipientUserId);
        return updatedMessages;
    }

    @Override
    public Map<UUID, Long> getUnreadMessageCountsPerChatForUser(UUID recipientUserId) {
        log.info("Getting unread message counts per chat for recipientUserId: {}", recipientUserId);
        List<MessageStatus> unreadStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);
        List<ChatMessage> allUnreadMessagesForUser = chatMessageRepository.findByRecipientIdAndStatusIn(recipientUserId, unreadStatuses);

        if (allUnreadMessagesForUser.isEmpty()) {
            log.info("No unread messages found for recipientUserId: {}.", recipientUserId);
            return Collections.emptyMap();
        }

        Map<UUID, Long> unreadCounts = allUnreadMessagesForUser.stream()
                .collect(Collectors.groupingBy(ChatMessage::getChatId, Collectors.counting()));

        log.info("Calculated unread message counts for recipientUserId: {}. Counts: {}", recipientUserId, unreadCounts);
        return unreadCounts;
    }
}
