package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.controller.dto.UserPayloadDto;
import com.spshpau.chatservice.model.User;
import org.springframework.messaging.handler.annotation.Payload;

public interface UserController {
    /**
     * Handles user connection based on data sent in payload.
     * @param payload DTO containing user identifiers.
     * @return The User object after being marked ONLINE.
     */
    User addUser(@Payload UserPayloadDto payload);

    /**
     * Handles user disconnection based on data sent in payload.
     * @param payload DTO containing user identifiers (at least userId).
     * @return The User object after being marked OFFLINE.
     */
    User disconnect(@Payload UserPayloadDto payload);
}
