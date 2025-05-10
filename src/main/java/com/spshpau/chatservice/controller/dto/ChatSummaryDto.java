package com.spshpau.chatservice.controller.dto;

import com.spshpau.chatservice.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSummaryDto {
    private User chatPartner;
    private UUID chatId;
    private long unreadCount;
}
