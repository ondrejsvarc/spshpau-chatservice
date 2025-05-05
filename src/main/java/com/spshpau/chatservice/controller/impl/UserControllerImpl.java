package com.spshpau.chatservice.controller.impl;

import com.spshpau.chatservice.controller.UserController;
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
    @SendTo("/user/topic")
    public User addUser(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Extract details directly from the injected Jwt object
        String sub = jwt.getClaimAsString(JwtClaimNames.SUB);
        String username = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        if (sub == null || username == null) {
            return null;
        }

        try {
            UUID userId = UUID.fromString(sub);
            return userService.saveUser(userId, username, firstName, lastName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @MessageMapping("/user.disconnectUser")
    @SendTo("/user/topic")
    public User disconnect(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        String sub = jwt.getClaimAsString(JwtClaimNames.SUB);

        if (sub == null) {
            return null;
        }

        try {
            UUID userId = UUID.fromString(sub);
            return userService.disconnect(userId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> findConnectedUsers() {
        return ResponseEntity.ok(userService.findConnectedUsers());
    }
}
