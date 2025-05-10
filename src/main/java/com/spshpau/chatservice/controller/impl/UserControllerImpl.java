package com.spshpau.chatservice.controller.impl;

import com.spshpau.chatservice.controller.UserController;
import com.spshpau.chatservice.controller.dto.ChatSummaryDto;
import com.spshpau.chatservice.controller.dto.UserPayloadDto;
import com.spshpau.chatservice.controller.notifications.MessageStatusUpdateNotification;
import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.services.ChatMessageService;
import com.spshpau.chatservice.services.ChatRoomService;
import com.spshpau.chatservice.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UserControllerImpl implements UserController {

    private final UserService userService;
    private final ChatMessageService chatMessageService;
    private final ChatRoomService chatRoomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @MessageMapping("/user.addUser")
    @SendTo("/topic/presence")
    public User addUser(@Payload UserPayloadDto payload) {
        if (payload == null || payload.getUserId() == null || payload.getUsername() == null) {
            log.warn("AddUser: Received null payload or missing userId/username.");
            return null;
        }
        try {
            UUID userId = UUID.fromString(payload.getUserId());
            return userService.saveUser(userId, payload.getUsername(), payload.getFirstName(), payload.getLastName(), false);
        } catch (IllegalArgumentException e) {
            log.error("AddUser: Invalid UUID format in payload userId: {}", payload.getUserId(), e);
            return null;
        } catch (Exception e) {
            log.error("AddUser: Error processing for payload: {}", payload, e);
            return null;
        }
    }

    @Override
    @MessageMapping("/user.disconnectUser")
    @SendTo("/topic/presence")
    public User disconnect(@Payload UserPayloadDto payload) {
        if (payload == null || payload.getUserId() == null) {
            log.warn("DisconnectUser: Received null payload or missing userId.");
            return null;
        }
        try {
            UUID userId = UUID.fromString(payload.getUserId());
            return userService.disconnect(userId);
        } catch (IllegalArgumentException e) {
            log.error("DisconnectUser: Invalid UUID format in payload userId: {}", payload.getUserId(), e);
            return null;
        } catch (Exception e) {
            log.error("DisconnectUser: Error processing for payload: {}", payload, e);
            return null;
        }
    }

    @Override
    @GetMapping("/api/v1/chats/users")
    public ResponseEntity<List<User>> findConnectedUsers() {
        return ResponseEntity.ok(userService.findConnectedUsers());
    }

    @Override
    @GetMapping("/api/v1/chats/me")
    public ResponseEntity<List<User>> getMyChats(@AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(userService.findMyChats(jwt));
        } catch (Exception e) {
            log.error("Error fetching myChats for user {}", jwt.getClaimAsString(JwtClaimNames.SUB), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    @GetMapping("/api/v1/chats/summary")
    public ResponseEntity<List<ChatSummaryDto>> getMyChatSummaries(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            log.warn("Attempted to get chat summaries without authentication.");
            return ResponseEntity.status(401).build();
        }
        try {
            UUID currentUserUuid = UUID.fromString(jwt.getClaimAsString(JwtClaimNames.SUB));
            log.info("Fetching chat summaries for user {}", currentUserUuid);

            List<ChatMessage> newlyDeliveredMessages = chatMessageService.markSentMessagesToUserAsDelivered(currentUserUuid);

            for (ChatMessage deliveredMsg : newlyDeliveredMessages) {
                MessageStatusUpdateNotification statusUpdate = MessageStatusUpdateNotification.builder()
                        .chatId(deliveredMsg.getChatId())
                        .messageIds(List.of(deliveredMsg.getId()))
                        .newStatus(deliveredMsg.getStatus())
                        .statusTimestamp(deliveredMsg.getDeliveredAt())
                        .updatedByUserId(currentUserUuid)
                        .build();
                messagingTemplate.convertAndSendToUser(
                        deliveredMsg.getSenderId().toString(),
                        "/queue/status-updates",
                        statusUpdate
                );
                log.info("Sent DELIVERED status update for message {} to original sender {}", deliveredMsg.getId(), deliveredMsg.getSenderId());
            }

            List<User> chatPartners = userService.findMyChats(jwt);
            if (chatPartners == null) {
                chatPartners = new ArrayList<>();
            }

            Map<UUID, Long> unreadCountsByChatId = chatMessageService.getUnreadMessageCountsPerChatForUser(currentUserUuid);

            List<ChatSummaryDto> summaries = new ArrayList<>();
            for (User partner : chatPartners) {
                if (partner.getId().equals(currentUserUuid)) continue;

                Optional<UUID> optChatId = chatRoomService.getChatRoomId(currentUserUuid, partner.getId(), false);
                if (optChatId.isPresent()) {
                    UUID chatId = optChatId.get();
                    long unreadCount = unreadCountsByChatId.getOrDefault(chatId, 0L);
                    summaries.add(new ChatSummaryDto(partner, chatId, unreadCount));
                } else {
                    summaries.add(new ChatSummaryDto(partner, null, 0L));
                    log.warn("No persisted chat room ID found between {} and {} for summary, unread count will be 0.", currentUserUuid, partner.getId());
                }
            }
            return ResponseEntity.ok(summaries);

        } catch (IllegalArgumentException e) {
            log.error("Error processing getMyChatSummaries due to invalid UUID for user", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("General error processing getMyChatSummaries for user {}", jwt.getClaimAsString(JwtClaimNames.SUB), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
