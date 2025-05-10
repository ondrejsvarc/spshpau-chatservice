package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.controller.dto.UserSummaryDto;
import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.model.enums.StatusEnum;
import com.spshpau.chatservice.otherservices.UserClient;
import com.spshpau.chatservice.repositories.UserRepository;
import com.spshpau.chatservice.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserClient userClient;

    @Override
    public User saveUser(UUID userId, String username, String firstName, String lastName, boolean fetch) {
        log.info("Attempting to save or update user with ID: {}, Username: {}", userId, username);
        User user = userRepository.findById(userId)
                .orElse(new User());

        boolean isNewUser = user.getId() == null;
        if (isNewUser) {
            log.debug("User with ID: {} not found, creating new user.", userId);
            user.setId(userId);
        } else {
            log.debug("User with ID: {} found, updating existing user.", userId);
        }

        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        if (!fetch) {
            user.setStatus(StatusEnum.ONLINE);
        }

        User savedUser = userRepository.save(user);
        log.info("{} user with ID: {}, Username: {} successfully. Status set to ONLINE.",
                isNewUser ? "Saved new" : "Updated", savedUser.getId(), savedUser.getUsername());
        return savedUser;
    }

    @Override
    public User disconnect(UUID userId) {
        log.info("Attempting to disconnect user with ID: {}", userId);
        User storedUser = userRepository.findById(userId)
                .orElse(null);

        if (storedUser != null) {
            storedUser.setStatus(StatusEnum.OFFLINE);
            User savedUser = userRepository.save(storedUser);
            log.info("User with ID: {} successfully disconnected. Status set to OFFLINE.", userId);
            return savedUser;
        } else {
            log.warn("User with ID: {} not found for disconnection.", userId);
            return null;
        }
    }

    @Override
    public List<User> findConnectedUsers() {
        log.info("Fetching all connected (ONLINE) users.");
        List<User> connectedUsers = userRepository.findAllByStatus(StatusEnum.ONLINE);
        log.info("Found {} connected users.", connectedUsers.size());
        return connectedUsers;
    }

    @Override
    public List<User> findMyChats(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        log.info("Fetching 'my chats' for user ID (from JWT sub): {}, Username: {}", keycloakId, username);

        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        if (keycloakId == null || username == null) {
            log.warn("Cannot find chats: Keycloak ID or username is null from JWT. User: {}", keycloakId);
            return new ArrayList<>();
        }

        UUID keycloakUuid;
        try {
            keycloakUuid = UUID.fromString(keycloakId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format for keycloakId: {} from JWT subject.", keycloakId, e);
            return new ArrayList<>();
        }

        log.debug("Ensuring local user record exists for user ID: {}, Username: {}", keycloakUuid, username);
        saveUser(keycloakUuid, username, firstName, lastName, false);

        String tokenValue = jwt.getTokenValue();
        String bearerToken = "Bearer " + tokenValue;

        log.debug("Calling UserClient to find connections for user ID: {}", keycloakUuid);
        List<UserSummaryDto> connections;
        try {
            connections = userClient.findConnectionsByJwt(bearerToken);
            log.info("Received {} connections from UserClient for user ID: {}", connections.size(), keycloakUuid);
        } catch (Exception e) {
            log.error("Error calling UserClient to find connections for user ID: {}.", keycloakUuid, e);
            return new ArrayList<>();
        }

        List<User> chats = new ArrayList<>();
        for ( UserSummaryDto dto : connections ) {
            if (dto.getId() == null) {
                log.warn("Skipping connection with null ID: {}", dto);
                continue;
            }
            log.debug("Processing connection DTO: ID={}, Username={}", dto.getId(), dto.getUsername());
            User chatPartner = saveUser(dto.getId(), dto.getUsername(), dto.getFirstName(), dto.getLastName(), true);
            chats.add(chatPartner);
        }

        log.info("Successfully processed {} chat partners for user ID: {}", chats.size(), keycloakUuid);
        return chats;
    }
}
