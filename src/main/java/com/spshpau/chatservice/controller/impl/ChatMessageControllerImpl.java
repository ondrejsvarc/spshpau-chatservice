package com.spshpau.chatservice.controller.impl;

import com.spshpau.chatservice.controller.ChatMessageController;
import com.spshpau.chatservice.controller.dto.MarkAsReadPayloadDto;
import com.spshpau.chatservice.controller.notifications.ChatNotification;
import com.spshpau.chatservice.controller.notifications.MessageStatusUpdateNotification;
import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.services.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatMessageControllerImpl implements ChatMessageController {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;

    @Override
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage) {
        ChatMessage savedMsg = chatMessageService.save(chatMessage);

        ChatNotification notification = ChatNotification.builder()
                .id(savedMsg.getId())
                .senderId(savedMsg.getSenderId())
                .recipientId(savedMsg.getRecipientId())
                .content(savedMsg.getContent())
                .sentAt(savedMsg.getSentAt())
                .status(savedMsg.getStatus())
                .chatId(savedMsg.getChatId())
                .statusTimestamp(savedMsg.getSentAt())
                .build();

        messagingTemplate.convertAndSendToUser(
                savedMsg.getRecipientId().toString(),
                "/queue/messages",
                notification
        );
        log.info("Attempted to send new message notification {} to user UUID {}", savedMsg.getId(), savedMsg.getRecipientId());
    }

    @Override
    @MessageMapping("/chat.markAsRead")
    public void markMessagesAsReadByRecipient(@Payload MarkAsReadPayloadDto payload,
                                              SimpMessageHeaderAccessor headerAccessor) {
        Principal principal = headerAccessor.getUser();
        if (principal == null || principal.getName() == null) {
            log.warn("Cannot mark messages as read: User not authenticated in STOMP session.");
            return;
        }
        if (payload == null || payload.getChatId() == null) {
            log.warn("Cannot mark messages as read: Received null payload or missing chatId.");
            return;
        }

        try {
            UUID currentUserUuid = UUID.fromString(principal.getName());
            UUID chatId = UUID.fromString(payload.getChatId());

            log.info("User {} marking messages as read for chat {}", currentUserUuid, chatId);
            List<ChatMessage> updatedMessages = chatMessageService.markMessagesAsRead(chatId, currentUserUuid);

            for (ChatMessage updatedMsg : updatedMessages) {
                MessageStatusUpdateNotification statusUpdate = MessageStatusUpdateNotification.builder()
                        .chatId(updatedMsg.getChatId())
                        .messageIds(List.of(updatedMsg.getId()))
                        .newStatus(updatedMsg.getStatus())
                        .statusTimestamp(updatedMsg.getReadAt())
                        .updatedByUserId(currentUserUuid)
                        .build();

                messagingTemplate.convertAndSendToUser(
                        updatedMsg.getSenderId().toString(),
                        "/queue/status-updates",
                        statusUpdate
                );
                log.info("Sent READ status update for message {} to original sender {}", updatedMsg.getId(), updatedMsg.getSenderId());
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for markAsRead: chatId {} or user {}", payload.getChatId(), principal.getName(), e);
        } catch (Exception e) {
            log.error("Error marking messages as read for chat {}: {}", payload.getChatId(), e.getMessage(), e);
        }
    }


    @Override
    @GetMapping("/messages/{senderId}/{recipientId}")
    public ResponseEntity<List<ChatMessage>> findChatMessages (
            @PathVariable("senderId") UUID senderId,
            @PathVariable("recipientId") UUID recipientId
    ) {
        return ResponseEntity.ok(chatMessageService.findChatMessages(senderId, recipientId));
    }
}
