package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.controller.dto.ChatSummaryDto;
import com.spshpau.chatservice.controller.dto.UserPayloadDto;
import com.spshpau.chatservice.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface UserController {

    User addUser(@Payload UserPayloadDto payload);

    User disconnect(@Payload UserPayloadDto payload);

    ResponseEntity<List<User>> findConnectedUsers();

    ResponseEntity<List<User>> getMyChats(Jwt jwt);

    ResponseEntity<List<ChatSummaryDto>> getMyChatSummaries(Jwt jwt);
}
