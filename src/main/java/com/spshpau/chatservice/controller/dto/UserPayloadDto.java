package com.spshpau.chatservice.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPayloadDto {
    private String userId;
    private String username;
    private String firstName;
    private String lastName;
}
