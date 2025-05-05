package com.spshpau.chatservice.repositories;

import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.model.enums.StatusEnum;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface UserRepository extends MongoRepository<User, UUID> {
    List<User> findAllByStatus(StatusEnum status);
}
