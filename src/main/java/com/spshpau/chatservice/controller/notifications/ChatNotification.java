package com.spshpau.chatservice.controller.notifications;

import com.spshpau.chatservice.model.enums.MessageStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatNotification {
    private UUID id;
    private UUID senderId;
    private UUID recipientId;
    private String content;
    private Instant sentAt;
    private MessageStatus status;
    private UUID chatId;
    private Instant statusTimestamp;
}
