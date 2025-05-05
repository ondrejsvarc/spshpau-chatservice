package com.spshpau.chatservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
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
    private Date timestamp;
}
