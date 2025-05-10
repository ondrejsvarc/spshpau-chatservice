package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatRoom;
import com.spshpau.chatservice.repositories.ChatRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceImplTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private ChatRoomServiceImpl chatRoomService;

    private UUID senderId;
    private UUID recipientId;

    @BeforeEach
    void setUp() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        if (id1.toString().compareTo(id2.toString()) > 0) {
            senderId = id1;
            recipientId = id2;
        } else {
            senderId = id2;
            recipientId = id1;
        }
        UUID recipientIdLexicographicallySmaller = UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    private UUID generateExpectedChatId(UUID id1, UUID id2) {
        String combinedString;
        if (id1.compareTo(id2) < 0) {
            combinedString = id1.toString() + "|" + id2.toString();
        } else {
            combinedString = id2.toString() + "|" + id1.toString();
        }
        return UUID.nameUUIDFromBytes(combinedString.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void getChatRoomId_whenRoomExists_shouldReturnExistingChatId() {
        UUID existingChatId = UUID.randomUUID();
        ChatRoom existingRoom = ChatRoom.builder()
                .id(UUID.randomUUID())
                .chatId(existingChatId)
                .senderId(senderId)
                .recipientId(recipientId)
                .build();

        when(chatRoomRepository.findBySenderIdAndRecipientId(senderId, recipientId)).thenReturn(Optional.of(existingRoom));

        Optional<UUID> result = chatRoomService.getChatRoomId(senderId, recipientId, true);

        assertTrue(result.isPresent());
        assertEquals(existingChatId, result.get());
        verify(chatRoomRepository, times(1)).findBySenderIdAndRecipientId(senderId, recipientId);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void getChatRoomId_whenRoomDoesNotExistAndCreateNewIsTrue_shouldCreateAndReturnNewChatId() {
        when(chatRoomRepository.findBySenderIdAndRecipientId(senderId, recipientId)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UUID> result = chatRoomService.getChatRoomId(senderId, recipientId, true);

        assertTrue(result.isPresent());
        assertNotNull(result.get());

        UUID expectedChatId = generateExpectedChatId(senderId, recipientId);
        assertEquals(expectedChatId, result.get());

        verify(chatRoomRepository, times(1)).findBySenderIdAndRecipientId(senderId, recipientId);

        ArgumentCaptor<ChatRoom> chatRoomCaptor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository, times(2)).save(chatRoomCaptor.capture());

        List<ChatRoom> savedRooms = chatRoomCaptor.getAllValues();
        assertEquals(2, savedRooms.size());

        ChatRoom room1 = savedRooms.get(0);
        ChatRoom room2 = savedRooms.get(1);

        assertEquals(expectedChatId, room1.getChatId());
        assertEquals(expectedChatId, room2.getChatId());

        boolean senderRecipientPairFound = (room1.getSenderId().equals(senderId) && room1.getRecipientId().equals(recipientId)) ||
                (room2.getSenderId().equals(senderId) && room2.getRecipientId().equals(recipientId));
        boolean recipientSenderPairFound = (room1.getSenderId().equals(recipientId) && room1.getRecipientId().equals(senderId)) ||
                (room2.getSenderId().equals(recipientId) && room2.getRecipientId().equals(senderId));
        assertTrue(senderRecipientPairFound, "Sender-Recipient pair not saved correctly");
        assertTrue(recipientSenderPairFound, "Recipient-Sender pair not saved correctly");
        assertNotEquals(room1.getId(), room2.getId(), "Primary keys for the two room documents should be different.");
    }


    @Test
    void getChatRoomId_whenRoomDoesNotExistAndCreateNewIsTrue_orderOfIdsDoesNotAffectChatId() {
        UUID idLarge = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID idSmall = UUID.fromString("00000000-0000-0000-0000-000000000000");

        when(chatRoomRepository.findBySenderIdAndRecipientId(any(UUID.class), any(UUID.class))).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UUID> result1 = chatRoomService.getChatRoomId(idLarge, idSmall, true);
        assertTrue(result1.isPresent());
        UUID chatId1 = result1.get();
        clearInvocations(chatRoomRepository);
        when(chatRoomRepository.findBySenderIdAndRecipientId(any(UUID.class), any(UUID.class))).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UUID> result2 = chatRoomService.getChatRoomId(idSmall, idLarge, true);
        assertTrue(result2.isPresent());
        UUID chatId2 = result2.get();

        assertEquals(chatId1, chatId2, "ChatId should be the same regardless of sender/recipient order for creation.");

        UUID expectedChatId = generateExpectedChatId(idLarge, idSmall);
        assertEquals(expectedChatId, chatId1);
        assertEquals(expectedChatId, chatId2);
    }


    @Test
    void getChatRoomId_whenRoomDoesNotExistAndCreateNewIsFalse_shouldReturnEmptyOptional() {
        when(chatRoomRepository.findBySenderIdAndRecipientId(senderId, recipientId)).thenReturn(Optional.empty());

        Optional<UUID> result = chatRoomService.getChatRoomId(senderId, recipientId, false);

        assertTrue(result.isEmpty());
        verify(chatRoomRepository, times(1)).findBySenderIdAndRecipientId(senderId, recipientId);
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void createChatRoomEntries_internalLogicVerificationViaGetChatRoomId() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        if (userA.toString().compareTo(userB.toString()) > 0) {
            UUID temp = userA;
            userA = userB;
            userB = temp;
        }

        when(chatRoomRepository.findBySenderIdAndRecipientId(userA, userB)).thenReturn(Optional.empty());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UUID> generatedChatIdOpt = chatRoomService.getChatRoomId(userA, userB, true);
        assertTrue(generatedChatIdOpt.isPresent());
        UUID generatedChatId = generatedChatIdOpt.get();

        String expectedCombinedString = userA.toString() + "|" + userB.toString();
        UUID expectedChatId = UUID.nameUUIDFromBytes(expectedCombinedString.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedChatId, generatedChatId);

        ArgumentCaptor<ChatRoom> captor = ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository, times(2)).save(captor.capture());
        List<ChatRoom> savedRooms = captor.getAllValues();

        UUID finalUserA = userA;
        UUID finalUserB = userB;
        ChatRoom roomForAtoB = savedRooms.stream()
                .filter(r -> r.getSenderId().equals(finalUserA) && r.getRecipientId().equals(finalUserB))
                .findFirst().orElse(null);
        ChatRoom roomForBtoA = savedRooms.stream()
                .filter(r -> r.getSenderId().equals(finalUserB) && r.getRecipientId().equals(finalUserA))
                .findFirst().orElse(null);

        assertNotNull(roomForAtoB);
        assertNotNull(roomForBtoA);

        assertEquals(expectedChatId, roomForAtoB.getChatId());
        assertEquals(userA, roomForAtoB.getSenderId());
        assertEquals(userB, roomForAtoB.getRecipientId());
        assertNotNull(roomForAtoB.getId());

        assertEquals(expectedChatId, roomForBtoA.getChatId());
        assertEquals(userB, roomForBtoA.getSenderId());
        assertEquals(userA, roomForBtoA.getRecipientId());
        assertNotNull(roomForBtoA.getId());

        assertNotEquals(roomForAtoB.getId(), roomForBtoA.getId(), "Primary keys of the two ChatRoom documents must be unique.");
    }
}