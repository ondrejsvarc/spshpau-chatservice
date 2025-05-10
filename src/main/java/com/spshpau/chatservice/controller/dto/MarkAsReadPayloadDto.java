package com.spshpau.chatservice.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarkAsReadPayloadDto {
    private String chatId;
}