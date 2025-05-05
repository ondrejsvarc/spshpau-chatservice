package com.spshpau.chatservice.controller.notifications;

import lombok.*;

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
}
