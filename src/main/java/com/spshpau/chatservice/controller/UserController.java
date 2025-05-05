package com.spshpau.chatservice.controller;

import com.spshpau.chatservice.model.User;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserController {
    /**
     * Handles user connection based on the provided JWT.
     * Implementation will extract details from the JWT.
     *
     * @param jwt The authenticated user's JWT.
     * @return The User object after being marked ONLINE.
     */
    User addUser(Jwt jwt);

    /**
     * Handles user disconnection based on the provided JWT.
     * Implementation will extract details from the JWT.
     *
     * @param jwt The authenticated user's JWT.
     * @return The User object after being marked OFFLINE.
     */
    User disconnect(Jwt jwt);
}
