package com.spshpau.chatservice.controller.notifications;

import com.spshpau.chatservice.model.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusUpdateNotification {
    private UUID chatId;
    private List<UUID> messageIds;
    private MessageStatus newStatus;
    private Instant statusTimestamp;
    private UUID updatedByUserId;
}
