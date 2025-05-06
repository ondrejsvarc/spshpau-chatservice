package com.spshpau.chatservice.controller.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
public class UserSummaryDto {
    private UUID id;
    private String username;
    private String firstName;
    private String lastName;
    private String location;

    private ArtistProfileSummaryDto artistProfile;
    private ProducerProfileSummaryDto producerProfile;

    public UserSummaryDto(UUID id, String username, String firstName, String lastName, String location) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.location = location;
    }
}
