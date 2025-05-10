'use strict';

// Page elements
const loadingPage = document.querySelector('#loading-page');
const chatPage = document.querySelector('#chat-page');
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const chatArea = document.querySelector('#chat-messages');
const connectingElement = document.querySelector('.connecting');
const connectedUserFullnameElement = document.querySelector('#connected-user-fullname');
const connectedUsersList = document.getElementById('connectedUsers');
const logout = document.querySelector('#logout');

// OIDC Configuration
const oidcConfig = {
    authority: 'http://192.168.1.112:8080/realms/SPSHPAU',
    client_id: 'spshpau-rest-api',
    redirect_uri: 'http://localhost:8091/',
    post_logout_redirect_uri: 'http://localhost:8091/',
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    loadUserInfo: true,
    filterProtocolClaims: true,
};

if (typeof oidc === 'undefined') {
    console.error('oidc-client-ts library not found.');
    connectingElement.textContent = 'Error: OIDC Client library not found.';
    throw new Error('oidc-client-ts not loaded');
}
const userManager = new oidc.UserManager(oidcConfig);

// Global State
let username = null;
let userUUID = null;
let fullname = null;
let stompClient = null;
let selectedUserUuid = null;
let selectedChatId = null;
let currentUser = null;

let chatRefreshIntervalId = null;
let chatListRefreshIntervalId = null;
const REFRESH_INTERVAL_MS = 5000;

let isFetchingChatHistory = false;

// --- Authentication & Initialization ---
async function getUser() {
    currentUser = await userManager.getUser();
    if (currentUser && !currentUser.expired) {
        username = currentUser.profile.preferred_username;
        userUUID = currentUser.profile.sub;
        fullname = currentUser.profile.name || username;
        return currentUser;
    }
    userUUID = null;
    return null;
}

async function login() {
    try {
        await userManager.signinRedirect({ state: window.location.pathname });
    } catch (error) {
        console.error('Error during signinRedirect:', error);
        connectingElement.textContent = 'Error initiating login.';
        connectingElement.style.color = 'red';
    }
}

async function logoutUser() {
    console.log('Logging out...');
    if (chatRefreshIntervalId) {
        clearInterval(chatRefreshIntervalId);
        chatRefreshIntervalId = null;
        console.log('Cleared active chat refresh interval.');
    }
    if (chatListRefreshIntervalId) {
        clearInterval(chatListRefreshIntervalId);
        chatListRefreshIntervalId = null;
        console.log('Cleared chat list refresh interval.');
    }

    if (stompClient && stompClient.connected && userUUID) {
        const disconnectPayload = { userId: userUUID };
        stompClient.send("/app/user.disconnectUser", {}, JSON.stringify(disconnectPayload));
    }
    const user = await userManager.getUser();
    userManager.signoutRedirect({ id_token_hint: user ? user.id_token : undefined })
        .catch(err => console.error("Error during signoutRedirect:", err));
    chatPage.classList.add('hidden');
    loadingPage.classList.remove('hidden');
    connectingElement.textContent = 'Logging out...';
    userUUID = null;
    selectedUserUuid = null;
    selectedChatId = null;
}

async function initializeApp() {
    try {
        if (window.location.href.includes('code=') && window.location.href.includes('state=')) {
            const user = await userManager.signinRedirectCallback();
            window.history.replaceState({}, document.title, "/");
            await setupUIAndConnect(user);
        } else {
            const user = await getUser();
            if (user) {
                await setupUIAndConnect(user);
            } else {
                await login();
            }
        }
    } catch (error) {
        console.error('Error during application initialization:', error);
        connectingElement.textContent = 'Authentication error. Please try again.';
        connectingElement.style.color = 'red';
    }
}

async function setupUIAndConnect(user) {
    currentUser = user;
    username = user.profile.preferred_username;
    userUUID = user.profile.sub;
    fullname = user.profile.name || username;

    loadingPage.classList.add('hidden');
    chatPage.classList.remove('hidden');
    connectedUserFullnameElement.textContent = fullname;

    await connectWebSocket();
}

async function getAccessToken() {
    const user = await userManager.getUser();
    if (user && !user.expired) return user.access_token;
    await login();
    return null;
}

// --- WebSocket Connection ---
async function connectWebSocket() {
    if (!username) {
        console.error("Cannot connect to WebSocket: Username not available.");
        connectingElement.textContent = 'Authentication failed. Cannot connect to chat.';
        connectingElement.style.color = 'red';
        return;
    }

    if (stompClient !== null && stompClient.connected) {
        console.log('WebSocket already connected.');
        return;
    }

    const accessToken = await getAccessToken();
    if (!accessToken) {
        console.error("Cannot connect WebSocket: Failed to get valid access token.");
        connectingElement.textContent = 'Failed to get access token for chat connection.';
        connectingElement.style.color = 'red';
        return;
    }

    console.log('Connecting to WebSocket with userUUID:', userUUID);
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.connect({ 'Authorization': 'Bearer ' + accessToken }, onConnected, onError);
}

function onConnected() {
    console.log('WebSocket connected successfully.');
    connectingElement.textContent = '';

    stompClient.subscribe(`/user/${userUUID}/queue/messages`, onMessageReceived);
    stompClient.subscribe(`/user/${userUUID}/queue/status-updates`, onMessageStatusUpdate);
    stompClient.subscribe(`/topic/presence`, onPresenceUpdate);

    if (userUUID && username) {
        const connectPayload = {
            userId: userUUID,
            username: username,
            firstName: currentUser.profile.given_name,
            lastName: currentUser.profile.family_name
        };
        stompClient.send("/app/user.addUser", {}, JSON.stringify(connectPayload));
    }

    findAndDisplayChatSummaries().then(() => {
        if (chatListRefreshIntervalId) clearInterval(chatListRefreshIntervalId);
        chatListRefreshIntervalId = setInterval(findAndDisplayChatSummaries, REFRESH_INTERVAL_MS);
        console.log('Initial chat list displayed and summary refresh interval started.');
    }).catch(error => {
        console.error("Error during initial chat list display or starting interval:", error);
    });
}

function onError(error) {
    console.error('WebSocket Error:', error);
    connectingElement.textContent = 'WebSocket connection error. Please refresh.';
    connectingElement.style.color = 'red';

    if (chatRefreshIntervalId) {
        clearInterval(chatRefreshIntervalId);
        chatRefreshIntervalId = null;
        console.log('Cleared active chat refresh interval due to WebSocket error.');
    }
    if (chatListRefreshIntervalId) {
        clearInterval(chatListRefreshIntervalId);
        chatListRefreshIntervalId = null;
        console.log('Cleared chat list refresh interval due to WebSocket error.');
    }
}

// --- Presence & Status Updates ---
function onPresenceUpdate(payload) {
    try {
        const updatedUser = JSON.parse(payload.body);
        if (!updatedUser || !updatedUser.id) return;
        const listItem = connectedUsersList.querySelector(`[data-user-id="${updatedUser.id}"]`);
        if (listItem) {
            updateOnlineIndicator(listItem, updatedUser.status === 'ONLINE');
        }
    } catch (e) { console.error("Error processing presence update:", e); }
}

function onMessageStatusUpdate(payload) {
    console.log('Message status update received:', payload);
    try {
        const statusUpdate = JSON.parse(payload.body);
        if (!statusUpdate || !statusUpdate.messageIds || !statusUpdate.newStatus) return;

        statusUpdate.messageIds.forEach(messageId => {
            const messageElement = chatArea.querySelector(`[data-message-id="${messageId}"]`);
            if (messageElement) {
                updateMessageStatusIndicator(messageElement, statusUpdate.newStatus);
            }
        });
    } catch (e) { console.error("Error processing message status update:", e); }
}

// --- Chat List & Summaries ---
async function findAndDisplayChatSummaries() {
    const accessToken = await getAccessToken();
    if (!accessToken) {
        connectedUsersList.innerHTML = '<li>Authentication error.</li>';
        return;
    }
    try {
        console.log('Fetching chat summaries from /chats/summary...');
        const response = await fetch('/chats/summary', {
            headers: { 'Authorization': 'Bearer ' + accessToken }
        });

        if (!response.ok) {
            if (response.status === 401) {
                console.warn("Unauthorized fetching chats. Token might be expired.");
                await login();
            }
            connectedUsersList.innerHTML = `<li>Error loading chats: ${response.statusText}</li>`;
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const chatSummaries = await response.json();
        connectedUsersList.innerHTML = '';
        if (chatSummaries.length === 0) {
            connectedUsersList.innerHTML = '<li>No chats found.</li>';
        } else {
            chatSummaries.forEach(summary => {
                appendChatPartnerElement(summary, connectedUsersList);
            });
        }
    } catch (error) {
        console.error('Error fetching chat summaries:', error);
        connectedUsersList.innerHTML = '<li>Could not load chats.</li>';
    }
}

function appendChatPartnerElement(summary, list) {
    const partner = summary.chatPartner;
    const listItem = document.createElement('li');
    listItem.classList.add('user-item');
    listItem.id = `user-item-${partner.username}`;
    listItem.dataset.userId = partner.id;
    listItem.dataset.username = partner.username;
    listItem.dataset.chatId = summary.chatId;

    const userImage = document.createElement('img');
    userImage.src = '../img/user_icon.png';
    userImage.alt = partner.fullName || partner.username;

    const usernameSpan = document.createElement('span');
    usernameSpan.textContent = partner.fullName || partner.username;

    listItem.appendChild(userImage);
    listItem.appendChild(usernameSpan);

    // Online Indicator
    const onlineIndicator = document.createElement('span');
    onlineIndicator.classList.add('online-indicator');
    updateOnlineIndicator(listItem, partner.status === 'ONLINE', onlineIndicator);
    listItem.appendChild(onlineIndicator);

    // Unread Messages Count
    const unreadMsgsSpan = document.createElement('span');
    unreadMsgsSpan.classList.add('nbr-msg');
    if (summary.unreadCount > 0) {
        unreadMsgsSpan.textContent = summary.unreadCount;
    } else {
        unreadMsgsSpan.classList.add('hidden');
    }
    listItem.appendChild(unreadMsgsSpan);

    listItem.addEventListener('click', userItemClick);
    list.appendChild(listItem);
}

function updateOnlineIndicator(listItem, isOnline, indicatorElement) {
    let indicator = indicatorElement || listItem.querySelector('.online-indicator');
    if (isOnline) {
        if (!indicator) {
            indicator = document.createElement('span');
            indicator.classList.add('online-indicator');
            const usernameSpan = listItem.querySelector('span');
            if (usernameSpan && usernameSpan.nextSibling) {
                usernameSpan.parentNode.insertBefore(indicator, usernameSpan.nextSibling);
            } else if (usernameSpan) {
                usernameSpan.parentNode.appendChild(indicator);
            } else {
                listItem.appendChild(indicator);
            }
        }
        indicator.classList.remove('offline');
    } else {
        if (indicator) {
            indicator.classList.add('offline');
        }
    }
}


async function userItemClick(event) {
    document.querySelectorAll('.user-item').forEach(item => item.classList.remove('active'));
    messageForm.classList.remove('hidden');
    const clickedUserElement = event.currentTarget;
    clickedUserElement.classList.add('active');

    const newSelectedUserUuid = clickedUserElement.dataset.userId;
    const newSelectedChatId = clickedUserElement.dataset.chatId;

    console.log(`userItemClick: New selection - User UUID: ${newSelectedUserUuid}, Chat ID: ${newSelectedChatId}`);

    if (chatRefreshIntervalId) {
        clearInterval(chatRefreshIntervalId);
        chatRefreshIntervalId = null;
        console.log('userItemClick: Cleared previous active chat refresh interval.');
    }

    selectedUserUuid = newSelectedUserUuid;
    selectedChatId = newSelectedChatId;

    const unreadMsgsSpan = clickedUserElement.querySelector('.nbr-msg');
    if (unreadMsgsSpan) {
        unreadMsgsSpan.textContent = '0';
        unreadMsgsSpan.classList.add('hidden');
    }

    await fetchAndDisplayUserChat(selectedUserUuid, selectedChatId);

    if (selectedChatId && selectedChatId !== "null" && selectedChatId !== "undefined") {
        markMessagesAsRead(selectedChatId);

        chatRefreshIntervalId = setInterval(() => {
            refreshActiveChat(newSelectedUserUuid, newSelectedChatId);
        }, REFRESH_INTERVAL_MS);
        console.log(`userItemClick: Active chat refresh interval started for chat ID: ${newSelectedChatId}`);
    } else {
        console.warn("userItemClick: No valid chatId to mark messages as read or start refresh interval for user:", selectedUserUuid);
    }
}

async function refreshActiveChat(refreshForUserUuid, refreshForChatId) {
    console.log(`refreshActiveChat: Called for User UUID=${refreshForUserUuid}, Chat ID=${refreshForChatId}`);

    if (selectedUserUuid === refreshForUserUuid &&
        selectedChatId === refreshForChatId &&
        !chatPage.classList.contains('hidden') &&
        !messageForm.classList.contains('hidden')) {

        console.log(`Refreshing active chat with ${refreshForUserUuid} (Chat ID: ${refreshForChatId})`);
        await fetchAndDisplayUserChat(refreshForUserUuid, refreshForChatId);

        if (refreshForChatId && refreshForChatId !== "null" && refreshForChatId !== "undefined") {
            markMessagesAsRead(refreshForChatId);
        }
    } else {
        console.log(`refreshActiveChat: Skipped for ${refreshForUserUuid}. Current selection is User: ${selectedUserUuid}, Chat: ${selectedChatId}. Or chat not visible.`);
    }
}

// --- Chat Message Handling & Statuses ---
async function fetchAndDisplayUserChat(targetUserUuid, targetChatId) {
    console.log(`WorkspaceAndDisplayUserChat CALLED. Current logged-in userUUID: ${userUUID}, Fetching for targetUserUuid: ${targetUserUuid}`);
    if (isFetchingChatHistory) {
        console.log("fetchAndDisplayUserChat: Already fetching history, skipping this call.");
        return;
    }
    if (!targetUserUuid || !userUUID) {
        console.error("fetchAndDisplayUserChat: Missing target user UUID or current user UUID.", { targetUserUuid, userUUID });
        return;
    }
    isFetchingChatHistory = true;

    const accessToken = await getAccessToken();
    if (!accessToken) {
        console.error("fetchAndDisplayUserChat: Failed to get access token.");
        isFetchingChatHistory = false;
        return;
    }

    try {
        const response = await fetch(`/messages/${userUUID}/${targetUserUuid}`, {
            headers: { 'Authorization': 'Bearer ' + accessToken }
        });
        console.log('fetchAndDisplayUserChat - Response status:', response.status, 'for target:', targetUserUuid);

        if (!response.ok) {
            const errorText = await response.text();
            console.error(`WorkspaceAndDisplayUserChat - HTTP error! Status: ${response.status}, Body: ${errorText}`);
            if (targetUserUuid === selectedUserUuid) {
                chatArea.innerHTML = `<p>Error loading chat history: ${response.statusText}</p>`;
            }
            if (response.status === 401) { await login(); }
            isFetchingChatHistory = false;
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const messages = await response.json();
        console.log('fetchAndDisplayUserChat - Received messages:', messages, 'for target:', targetUserUuid);

        if (targetUserUuid === selectedUserUuid) {
            chatArea.innerHTML = '';
            if (messages.length === 0) {
                const selectedUserElement = connectedUsersList.querySelector(`[data-user-id="${targetUserUuid}"]`);
                const selectedUsernameDisplay = selectedUserElement ? selectedUserElement.dataset.username : targetUserUuid;
                chatArea.innerHTML = `<p>No messages with ${selectedUsernameDisplay} yet.</p>`;
            } else {
                messages.forEach(msg => {
                    displayMessage(msg.senderId, msg.content, msg.id, msg.status, msg.sentAt, msg.readAt || msg.deliveredAt || msg.sentAt);
                });
            }
            chatArea.scrollTop = chatArea.scrollHeight;
            console.log(`WorkspaceAndDisplayUserChat - Displayed ${messages.length} messages for target ${targetUserUuid}.`);
        } else {
            console.log(`WorkspaceAndDisplayUserChat: Fetched history for ${targetUserUuid}, but current selection is ${selectedUserUuid}. UI not updated.`);
        }

    } catch (error) {
        console.error('Error in fetchAndDisplayUserChat catch block:', error);
        if (targetUserUuid === selectedUserUuid) {
            chatArea.innerHTML = '<p>Could not load chat history due to an error.</p>';
        }
    } finally {
        isFetchingChatHistory = false;
    }
}

function sendMessage(event) {
    event.preventDefault();
    const messageContent = messageInput.value.trim();
    if (messageContent && stompClient && stompClient.connected && selectedUserUuid && userUUID) {
        const tempMessageId = `temp-${Date.now()}`;
        const sentAt = new Date().toISOString();

        displayMessage(userUUID, messageContent, tempMessageId, 'SENT', sentAt, sentAt);

        const chatMessagePayload = {
            senderId: userUUID,
            recipientId: selectedUserUuid,
            content: messageContent,
        };
        stompClient.send("/app/chat", {}, JSON.stringify(chatMessagePayload));
        messageInput.value = '';
    }
}

function onMessageReceived(payload) {
    console.log('Raw message (ChatNotification) received:', payload);
    try {
        const notification = JSON.parse(payload.body);
        displayMessage(notification.senderId, notification.content, notification.id, notification.status, notification.sentAt, notification.statusTimestamp);

        if (selectedChatId && selectedChatId === notification.chatId && notification.recipientId === userUUID) {
            console.log("New message is for active chat, marking as read immediately.");
            markMessagesAsRead(selectedChatId);
        } else if (notification.recipientId === userUUID) {
            const senderListItem = connectedUsersList.querySelector(`[data-user-id="${notification.senderId}"]`);
            if (senderListItem) {
                const unreadMsgsSpan = senderListItem.querySelector('.nbr-msg');
                if (unreadMsgsSpan) {
                    unreadMsgsSpan.classList.remove('hidden');
                    unreadMsgsSpan.textContent = (parseInt(unreadMsgsSpan.textContent) || 0) + 1;
                }
            }
        }
    } catch (e) { console.error("Error processing received message:", e); }
}

function displayMessage(senderId, content, messageId, status, sentAt, statusTimestamp) {
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('message');
    messageContainer.dataset.messageId = messageId;

    const messageText = document.createElement('p');
    messageText.textContent = content;

    const timeSpan = document.createElement('span');
    timeSpan.classList.add('message-timestamp');
    timeSpan.textContent = new Date(sentAt).toLocaleTimeString();

    const statusSpan = document.createElement('span');
    statusSpan.classList.add('message-status');

    if (senderId === userUUID) {
        messageContainer.classList.add('sender');
        updateMessageStatusIndicator(messageContainer, status);
    } else {
        messageContainer.classList.add('receiver');
    }

    messageContainer.appendChild(messageText);
    messageContainer.appendChild(timeSpan);

    chatArea.appendChild(messageContainer);
    chatArea.scrollTop = chatArea.scrollHeight;
}

// Helper to update status indicator on a message element
function updateMessageStatusIndicator(messageElement, newStatus) {
    let statusSpan = messageElement.querySelector('.message-status');
    if (!statusSpan && messageElement.classList.contains('sender')) {
        statusSpan = document.createElement('span');
        statusSpan.classList.add('message-status');
        const pTag = messageElement.querySelector('p');
        if (pTag) {
            pTag.appendChild(statusSpan);
        } else {
            messageElement.appendChild(statusSpan);
        }
    }

    if (statusSpan) {
        statusSpan.classList.remove('sent', 'delivered', 'read');
        let statusText = '';
        switch (newStatus) {
            case 'SENT':
                statusText = '✓';
                statusSpan.classList.add('sent');
                break;
            case 'DELIVERED':
                statusText = '✓✓';
                statusSpan.classList.add('delivered');
                break;
            case 'READ':
                statusText = '✓✓';
                statusSpan.classList.add('read');
                break;
            default:
                statusText = '';
        }
        statusSpan.textContent = statusText;
    }
}


// New function to send markAsRead to backend
function markMessagesAsRead(chatIdToMark) {
    if (stompClient && stompClient.connected && chatIdToMark) {
        console.log(`Sending markAsRead for chatId: ${chatIdToMark}`);
        const payload = { chatId: chatIdToMark };
        stompClient.send("/app/chat.markAsRead", {}, JSON.stringify(payload));
    } else {
        console.warn("Cannot mark messages as read: STOMP not connected or no chatId.", {stomp: stompClient, chatId: chatIdToMark});
    }
}

// --- Event Listeners & App Start ---
messageForm.addEventListener('submit', sendMessage, true);
logout.addEventListener('click', logoutUser, true);
initializeApp();