package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.controller.dto.UserSummaryDto;
import com.spshpau.chatservice.model.User;
import com.spshpau.chatservice.model.enums.StatusEnum;
import com.spshpau.chatservice.otherservices.UserClient;
import com.spshpau.chatservice.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID testUserId;
    private String testUsername;
    private String testFirstName;
    private String testLastName;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUsername = "testUser";
        testFirstName = "Test";
        testLastName = "User";
        testUser = new User();
        testUser.setId(testUserId);
        testUser.setUsername(testUsername);
        testUser.setFirstName(testFirstName);
        testUser.setLastName(testLastName);
        testUser.setStatus(StatusEnum.OFFLINE);
    }

    @Test
    void saveUser_whenNewUser_shouldCreateAndSaveUserAsOnline() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            if (savedUser.getId() == null) savedUser.setId(testUserId);
            return savedUser;
        });

        User result = userService.saveUser(testUserId, testUsername, testFirstName, testLastName);

        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals(testUsername, result.getUsername());
        assertEquals(testFirstName, result.getFirstName());
        assertEquals(testLastName, result.getLastName());
        assertEquals(StatusEnum.ONLINE, result.getStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertEquals(testUserId, capturedUser.getId());
        assertEquals(testUsername, capturedUser.getUsername());
        assertEquals(StatusEnum.ONLINE, capturedUser.getStatus());
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void saveUser_whenExistingUser_shouldUpdateAndSaveUserAsOnline() {
        testUser.setStatus(StatusEnum.OFFLINE);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String newFirstName = "UpdatedTest";
        User result = userService.saveUser(testUserId, testUsername, newFirstName, testLastName);

        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals(testUsername, result.getUsername());
        assertEquals(newFirstName, result.getFirstName());
        assertEquals(StatusEnum.ONLINE, result.getStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User capturedUser = userCaptor.getValue();

        assertEquals(testUserId, capturedUser.getId());
        assertEquals(newFirstName, capturedUser.getFirstName());
        assertEquals(StatusEnum.ONLINE, capturedUser.getStatus());
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void disconnect_whenUserExists_shouldSetStatusOfflineAndSave() {
        testUser.setStatus(StatusEnum.ONLINE);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.disconnect(testUserId);

        assertNotNull(result);
        assertEquals(testUserId, result.getId());
        assertEquals(StatusEnum.OFFLINE, result.getStatus());
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void disconnect_whenUserDoesNotExist_shouldReturnNull() {
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        User result = userService.disconnect(testUserId);

        assertNull(result);
        verify(userRepository, times(1)).findById(testUserId);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findConnectedUsers_shouldReturnListOfOnlineUsers() {
        User user1 = new User(); user1.setId(UUID.randomUUID()); user1.setStatus(StatusEnum.ONLINE);
        User user2 = new User(); user2.setId(UUID.randomUUID()); user2.setStatus(StatusEnum.ONLINE);
        List<User> onlineUsers = List.of(user1, user2);

        when(userRepository.findAllByStatus(StatusEnum.ONLINE)).thenReturn(onlineUsers);

        List<User> result = userService.findConnectedUsers();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsAll(onlineUsers));
        verify(userRepository, times(1)).findAllByStatus(StatusEnum.ONLINE);
    }

    @Test
    void findMyChats_whenValidJwtAndConnectionsExist_shouldReturnChatPartners() {
        Jwt mockJwt = mock(Jwt.class);
        String currentUserIdStr = testUserId.toString();
        String currentUsername = "currentUser";
        String currentUserFirstName = "Current";
        String currentUserLastName = "User";

        when(mockJwt.getSubject()).thenReturn(currentUserIdStr);
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn(currentUsername);
        when(mockJwt.getClaimAsString("given_name")).thenReturn(currentUserFirstName);
        when(mockJwt.getClaimAsString("family_name")).thenReturn(currentUserLastName);
        when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");

        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            if (userToSave.getId() == null) userToSave.setId(UUID.randomUUID());
            return userToSave;
        });


        UUID partner1Id = UUID.randomUUID();
        UserSummaryDto partner1Dto = new UserSummaryDto(partner1Id, "partner1", "Partner", "One", "Location1");
        UUID partner2Id = UUID.randomUUID();
        UserSummaryDto partner2Dto = new UserSummaryDto(partner2Id, "partner2", "Partner", "Two", "Location2");
        List<UserSummaryDto> connectionDtos = List.of(partner1Dto, partner2Dto);

        when(userClient.findConnectionsByJwt("Bearer mockTokenValue")).thenReturn(connectionDtos);

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getId().equals(partner1Id) && u.getUsername().equals("partner1")));
        assertTrue(result.stream().anyMatch(u -> u.getId().equals(partner2Id) && u.getUsername().equals("partner2")));

        verify(userRepository, times(3)).save(any(User.class));
        verify(userClient, times(1)).findConnectionsByJwt("Bearer mockTokenValue");
    }

    @Test
    void findMyChats_whenValidJwtAndNoConnections_shouldReturnEmptyList() {
        Jwt mockJwt = mock(Jwt.class);
        String currentUserIdStr = testUserId.toString();
        String currentUsername = "currentUser";

        when(mockJwt.getSubject()).thenReturn(currentUserIdStr);
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn(currentUsername);
        when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");

        when(userRepository.findById(testUserId)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));


        when(userClient.findConnectionsByJwt("Bearer mockTokenValue")).thenReturn(Collections.emptyList());

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userClient, times(1)).findConnectionsByJwt("Bearer mockTokenValue");
        verify(userRepository, times(1)).save(argThat(user -> user.getId().equals(testUserId)));
    }

    @Test
    void findMyChats_whenJwtSubjectIsNull_shouldReturnEmptyList() {
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(null);
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn("someUser");


        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userClient, never()).findConnectionsByJwt(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findMyChats_whenJwtUsernameIsNull_shouldReturnEmptyList() {
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn(testUserId.toString());
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn(null);

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userClient, never()).findConnectionsByJwt(anyString());
        verify(userRepository, never()).save(any(User.class));
    }


    @Test
    void findMyChats_whenJwtSubjectIsInvalidUuid_shouldReturnEmptyList() {
        Jwt mockJwt = mock(Jwt.class);
        when(mockJwt.getSubject()).thenReturn("invalid-uuid-string");
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn("someUser");

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userClient, never()).findConnectionsByJwt(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void findMyChats_whenUserClientThrowsException_shouldReturnEmptyList() {
        Jwt mockJwt = mock(Jwt.class);
        String currentUserIdStr = testUserId.toString();
        String currentUsername = "currentUser";

        when(mockJwt.getSubject()).thenReturn(currentUserIdStr);
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn(currentUsername);
        when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");

        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));


        when(userClient.findConnectionsByJwt("Bearer mockTokenValue")).thenThrow(new RuntimeException("UserClient failed"));

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(userClient, times(1)).findConnectionsByJwt("Bearer mockTokenValue");
        verify(userRepository, times(1)).save(argThat(user -> user.getId().equals(testUserId)));
    }

    @Test
    void findMyChats_whenConnectionDtoHasNullId_shouldSkipAndContinue() {
        Jwt mockJwt = mock(Jwt.class);
        String currentUserIdStr = testUserId.toString();
        String currentUsername = "currentUser";

        when(mockJwt.getSubject()).thenReturn(currentUserIdStr);
        when(mockJwt.getClaimAsString("preferred_username")).thenReturn(currentUsername);
        when(mockJwt.getTokenValue()).thenReturn("mockTokenValue");

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });

        UserSummaryDto validDto = new UserSummaryDto(UUID.randomUUID(), "validUser", "Valid", "User", "Loc");
        UserSummaryDto nullIdDto = new UserSummaryDto(null, "nullIdUser", "Null", "Id", "Loc"); // ID is null

        List<UserSummaryDto> connectionDtos = new ArrayList<>();
        connectionDtos.add(validDto);
        connectionDtos.add(nullIdDto);

        when(userClient.findConnectionsByJwt("Bearer mockTokenValue")).thenReturn(connectionDtos);

        List<User> result = userService.findMyChats(mockJwt);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(validDto.getId(), result.get(0).getId());

        verify(userRepository, times(2)).save(any(User.class));
    }
}