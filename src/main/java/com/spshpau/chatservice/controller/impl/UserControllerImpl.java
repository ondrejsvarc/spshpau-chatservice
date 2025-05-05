package com.spshpau.chatservice.controller.impl;

import com.spshpau.chatservice.controller.UserController;
import com.spshpau.chatservice.controller.dto.UserPayloadDto;
import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class UserControllerImpl implements UserController {

    private final UserService userService;

    @Override
    @MessageMapping("/user.addUser")
    @SendTo("/topic/presence")
    public User addUser(@Payload UserPayloadDto payload) {
        if (payload == null || payload.getUserId() == null || payload.getUsername() == null) {
            return null;
        }
        try {
            UUID userId = UUID.fromString(payload.getUserId());
            return userService.saveUser(userId, payload.getUsername(), payload.getFirstName(), payload.getLastName());
        } catch (IllegalArgumentException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @MessageMapping("/user.disconnectUser")
    @SendTo("/topic/presence")
    public User disconnect(@Payload UserPayloadDto payload) {
        if (payload == null || payload.getUserId() == null) {
            return null;
        }
        try {
            UUID userId = UUID.fromString(payload.getUserId());
            String username = payload.getUsername();
            return userService.disconnect(userId);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> findConnectedUsers() {
        return ResponseEntity.ok(userService.findConnectedUsers());
    }
}
