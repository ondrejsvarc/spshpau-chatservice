package com.spshpau.chatservice.services.impl;

import com.spshpau.chatservice.model.ChatMessage;
import com.spshpau.chatservice.model.enums.MessageStatus;
import com.spshpau.chatservice.repositories.ChatMessageRepository;
import com.spshpau.chatservice.services.ChatRoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @InjectMocks
    private ChatMessageServiceImpl chatMessageService;

    private UUID senderId;
    private UUID recipientId;
    private UUID chatId;
    private ChatMessage sampleChatMessage;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        recipientId = UUID.randomUUID();
        chatId = UUID.randomUUID();

        sampleChatMessage = ChatMessage.builder()
                .senderId(senderId)
                .recipientId(recipientId)
                .content("Hello")
                .build();
    }

    @Test
    void save_newMessage_shouldSetDefaultsAndSave() {
        when(chatRoomService.getChatRoomId(senderId, recipientId, true)).thenReturn(Optional.of(chatId));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage msgToSave = invocation.getArgument(0);
            if (msgToSave.getId() == null) {
                msgToSave.setId(UUID.randomUUID());
            }
            return msgToSave;
        });

        ChatMessage savedMessage = chatMessageService.save(sampleChatMessage);

        assertNotNull(savedMessage.getId());
        assertEquals(chatId, savedMessage.getChatId());
        assertEquals(MessageStatus.SENT, savedMessage.getStatus());
        assertNotNull(savedMessage.getSentAt());
        assertEquals(senderId, savedMessage.getSenderId());
        assertEquals(recipientId, savedMessage.getRecipientId());
        assertEquals("Hello", savedMessage.getContent());

        verify(chatRoomService, times(1)).getChatRoomId(senderId, recipientId, true);
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
    }

    @Test
    void save_whenChatRoomServiceFails_shouldThrowRuntimeException() {
        when(chatRoomService.getChatRoomId(senderId, recipientId, true)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> chatMessageService.save(sampleChatMessage));

        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void findChatMessages_whenChatRoomExists_shouldReturnMessages() {
        List<ChatMessage> messages = Collections.singletonList(ChatMessage.builder().chatId(chatId).content("Test").build());
        when(chatRoomService.getChatRoomId(senderId, recipientId, false)).thenReturn(Optional.of(chatId));
        when(chatMessageRepository.findByChatId(chatId)).thenReturn(messages);

        List<ChatMessage> foundMessages = chatMessageService.findChatMessages(senderId, recipientId);

        assertEquals(messages, foundMessages);
        verify(chatRoomService, times(1)).getChatRoomId(senderId, recipientId, false);
        verify(chatMessageRepository, times(1)).findByChatId(chatId);
    }

    @Test
    void findChatMessages_whenChatRoomDoesNotExist_shouldReturnEmptyList() {
        when(chatRoomService.getChatRoomId(senderId, recipientId, false)).thenReturn(Optional.empty());

        List<ChatMessage> foundMessages = chatMessageService.findChatMessages(senderId, recipientId);

        assertTrue(foundMessages.isEmpty());
        verify(chatRoomService, times(1)).getChatRoomId(senderId, recipientId, false);
        verify(chatMessageRepository, never()).findByChatId(any(UUID.class));
    }

    @Test
    void markMessagesAsDelivered_whenSentMessagesExist_shouldUpdateAndSave() {
        ChatMessage msg1 = ChatMessage.builder().id(UUID.randomUUID()).chatId(chatId).recipientId(recipientId).status(MessageStatus.SENT).build();
        ChatMessage msg2 = ChatMessage.builder().id(UUID.randomUUID()).chatId(chatId).recipientId(recipientId).status(MessageStatus.SENT).build();
        List<ChatMessage> messagesToUpdate = Arrays.asList(msg1, msg2);

        when(chatMessageRepository.findByChatIdAndRecipientIdAndStatus(chatId, recipientId, MessageStatus.SENT))
                .thenReturn(messagesToUpdate);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessage> updatedMessages = chatMessageService.markMessagesAsDelivered(chatId, recipientId);

        assertEquals(2, updatedMessages.size());
        updatedMessages.forEach(msg -> {
            assertEquals(MessageStatus.DELIVERED, msg.getStatus());
            assertNotNull(msg.getDeliveredAt());
        });
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
    }

    @Test
    void markMessagesAsDelivered_whenNoSentMessages_shouldReturnEmptyList() {
        when(chatMessageRepository.findByChatIdAndRecipientIdAndStatus(chatId, recipientId, MessageStatus.SENT))
                .thenReturn(Collections.emptyList());

        List<ChatMessage> updatedMessages = chatMessageService.markMessagesAsDelivered(chatId, recipientId);

        assertTrue(updatedMessages.isEmpty());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markMessagesAsRead_whenSentOrDeliveredMessagesExist_shouldUpdateAndSave() {
        ChatMessage msgSent = ChatMessage.builder().id(UUID.randomUUID()).chatId(chatId).recipientId(recipientId).status(MessageStatus.SENT).build();
        ChatMessage msgDelivered = ChatMessage.builder().id(UUID.randomUUID()).chatId(chatId).recipientId(recipientId).status(MessageStatus.DELIVERED).deliveredAt(Instant.now().minusSeconds(10)).build();
        List<ChatMessage> messagesToUpdate = Arrays.asList(msgSent, msgDelivered);
        List<MessageStatus> expectedStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);

        when(chatMessageRepository.findByChatIdAndRecipientIdAndStatusIn(chatId, recipientId, expectedStatuses))
                .thenReturn(messagesToUpdate);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessage> updatedMessages = chatMessageService.markMessagesAsRead(chatId, recipientId);

        assertEquals(2, updatedMessages.size());
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());

        List<ChatMessage> capturedMessages = captor.getAllValues();
        ChatMessage updatedSentMsg = capturedMessages.stream().filter(m -> m.getId().equals(msgSent.getId())).findFirst().orElseThrow();
        ChatMessage updatedDeliveredMsg = capturedMessages.stream().filter(m -> m.getId().equals(msgDelivered.getId())).findFirst().orElseThrow();

        assertEquals(MessageStatus.READ, updatedSentMsg.getStatus());
        assertNotNull(updatedSentMsg.getReadAt());
        assertNotNull(updatedSentMsg.getDeliveredAt());
        assertEquals(updatedSentMsg.getReadAt(), updatedSentMsg.getDeliveredAt());

        assertEquals(MessageStatus.READ, updatedDeliveredMsg.getStatus());
        assertNotNull(updatedDeliveredMsg.getReadAt());
        assertNotNull(updatedDeliveredMsg.getDeliveredAt());
        assertTrue(updatedDeliveredMsg.getReadAt().isAfter(updatedDeliveredMsg.getDeliveredAt()) || updatedDeliveredMsg.getReadAt().equals(updatedDeliveredMsg.getDeliveredAt()));


    }

    @Test
    void markMessagesAsRead_whenNoRelevantMessages_shouldReturnEmptyList() {
        List<MessageStatus> expectedStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);
        when(chatMessageRepository.findByChatIdAndRecipientIdAndStatusIn(chatId, recipientId, expectedStatuses))
                .thenReturn(Collections.emptyList());

        List<ChatMessage> updatedMessages = chatMessageService.markMessagesAsRead(chatId, recipientId);

        assertTrue(updatedMessages.isEmpty());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }

    @Test
    void markSentMessagesToUserAsDelivered_whenSentMessagesExist_shouldUpdate() {
        UUID userRecipientId = UUID.randomUUID();
        ChatMessage msg1 = ChatMessage.builder().id(UUID.randomUUID()).recipientId(userRecipientId).status(MessageStatus.SENT).build();
        List<ChatMessage> messagesToUpdate = Collections.singletonList(msg1);

        when(chatMessageRepository.findByRecipientIdAndStatus(userRecipientId, MessageStatus.SENT))
                .thenReturn(messagesToUpdate);
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ChatMessage> updatedMessages = chatMessageService.markSentMessagesToUserAsDelivered(userRecipientId);

        assertEquals(1, updatedMessages.size());
        assertEquals(MessageStatus.DELIVERED, updatedMessages.get(0).getStatus());
        assertNotNull(updatedMessages.get(0).getDeliveredAt());
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
    }

    @Test
    void markSentMessagesToUserAsDelivered_whenNoSentMessages_shouldReturnEmptyList() {
        UUID userRecipientId = UUID.randomUUID();
        when(chatMessageRepository.findByRecipientIdAndStatus(userRecipientId, MessageStatus.SENT))
                .thenReturn(Collections.emptyList());

        List<ChatMessage> updatedMessages = chatMessageService.markSentMessagesToUserAsDelivered(userRecipientId);

        assertTrue(updatedMessages.isEmpty());
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
    }


    @Test
    void getUnreadMessageCountsPerChatForUser_whenUnreadMessagesExist_shouldReturnCounts() {
        UUID recipientUser = UUID.randomUUID();
        UUID chat1 = UUID.randomUUID();
        UUID chat2 = UUID.randomUUID();

        ChatMessage msg1Chat1 = ChatMessage.builder().chatId(chat1).recipientId(recipientUser).status(MessageStatus.SENT).build();
        ChatMessage msg2Chat1 = ChatMessage.builder().chatId(chat1).recipientId(recipientUser).status(MessageStatus.DELIVERED).build();
        ChatMessage msg1Chat2 = ChatMessage.builder().chatId(chat2).recipientId(recipientUser).status(MessageStatus.SENT).build();
        ChatMessage msgReadChat1 = ChatMessage.builder().chatId(chat1).recipientId(recipientUser).status(MessageStatus.READ).build();

        List<ChatMessage> unreadMessages = Arrays.asList(msg1Chat1, msg2Chat1, msg1Chat2);
        List<MessageStatus> unreadStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);

        when(chatMessageRepository.findByRecipientIdAndStatusIn(recipientUser, unreadStatuses))
                .thenReturn(unreadMessages);

        Map<UUID, Long> counts = chatMessageService.getUnreadMessageCountsPerChatForUser(recipientUser);

        assertNotNull(counts);
        assertEquals(2, counts.size());
        assertEquals(2L, counts.get(chat1));
        assertEquals(1L, counts.get(chat2));
        assertNull(counts.get(UUID.randomUUID()));
    }

    @Test
    void getUnreadMessageCountsPerChatForUser_whenNoUnreadMessages_shouldReturnEmptyMap() {
        UUID recipientUser = UUID.randomUUID();
        List<MessageStatus> unreadStatuses = Arrays.asList(MessageStatus.SENT, MessageStatus.DELIVERED);

        when(chatMessageRepository.findByRecipientIdAndStatusIn(recipientUser, unreadStatuses))
                .thenReturn(Collections.emptyList());

        Map<UUID, Long> counts = chatMessageService.getUnreadMessageCountsPerChatForUser(recipientUser);

        assertNotNull(counts);
        assertTrue(counts.isEmpty());
    }
}