package com.spshpau.chatservice.model;

import com.spshpau.chatservice.model.enums.MessageStatus;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document
public class ChatMessage {
    @Id
    private UUID id;
    private UUID chatId;
    private UUID senderId;
    private UUID recipientId;
    private String content;

    private MessageStatus status;
    private Instant sentAt;
    private Instant deliveredAt;
    private Instant readAt;
}
