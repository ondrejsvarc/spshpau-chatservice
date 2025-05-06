package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.controller.dto.UserSummaryDto;
import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.model.enums.StatusEnum;
import com.spshpau.chatservice.otherservices.UserClient;
import com.spshpau.chatservice.repositories.UserRepository;
import com.spshpau.chatservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserClient userClient;

    @Override
    public User saveUser(UUID userId, String username, String firstName, String lastName) {
        User user = userRepository.findById(userId)
                .orElse(new User()); // Create new user if not found

        user.setId(userId);
        user.setUsername(username);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setStatus(StatusEnum.ONLINE);

        User savedUser = userRepository.save(user);
        return savedUser;
    }

    @Override
    public User disconnect(UUID userId) {
        User storedUser = userRepository.findById(userId)
                .orElse(null);

        if (storedUser != null) {
            storedUser.setStatus(StatusEnum.OFFLINE);
            User savedUser = userRepository.save(storedUser);
            return savedUser;
        } else {
            return null;
        }
    }

    @Override
    public List<User> findConnectedUsers() {
        return userRepository.findAllByStatus(StatusEnum.ONLINE);
    }

    @Override
    public List<User> findMyChats(Jwt jwt) {
        // --- Extract details from the authenticated user (JWT token) ---
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        // Basic validation
        if (keycloakId == null || username == null) {
            return new ArrayList<>();
        }

        UUID keycloakUuid;
        try {
            // *** Convert the String ID from JWT subject to UUID ***
            keycloakUuid = UUID.fromString(keycloakId);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID format received from Keycloak token subject: " + keycloakId);
            return new ArrayList<>();
        }

        var user = saveUser(keycloakUuid, username, firstName, lastName);

        // Extract the token value and prepare the Bearer token string
        String tokenValue = jwt.getTokenValue();
        String bearerToken = "Bearer " + tokenValue;

        var connections = userClient.findConnectionsByJwt(bearerToken); // Get connections from users microservice

        List<User> chats = new ArrayList<>();
        for ( UserSummaryDto dto : connections ) {
            UUID dto_id = dto.getId();
            String dto_username = dto.getUsername();
            String dto_firstName = dto.getFirstName();
            String dto_lastName = dto.getLastName();

            var dto_user = saveUser(dto_id, dto_username, dto_firstName, dto_lastName);

            chats.add(dto_user);
        }

        return chats;
    }
}
