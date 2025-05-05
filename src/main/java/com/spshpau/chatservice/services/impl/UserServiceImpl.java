package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.model.enums.StatusEnum;
import com.spshpau.chatservice.repositories.UserRepository;
import com.spshpau.chatservice.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

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
}
