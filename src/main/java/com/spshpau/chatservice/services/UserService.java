package com.spshpau.chatservice.services;

import com.spshpau.chatservice.model.User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

public interface UserService {
    /**
     * Saves or updates a user based on JWT details, marking them as ONLINE.
     * Creates the user if they don't exist based on userId (from JWT sub).
     *
     * @param userId      The user's unique ID (from JWT sub claim).
     * @param username    The user's username (from JWT preferred_username claim).
     * @param firstName   The user's first name (from JWT given_name claim, can be null).
     * @param lastName    The user's last name (from JWT family_name claim, can be null).
     * @return The saved or updated User object.
     */
    User saveUser(UUID userId, String username, String firstName, String lastName);

    /**
     * Marks a user as OFFLINE based on their ID.
     *
     * @param userId The unique ID of the user to disconnect.
     * @return The User object after marking as OFFLINE, or null if user not found.
     */
    User disconnect(UUID userId);

    List<User> findConnectedUsers();

    List<User> findMyChats(Jwt jwt);
}
