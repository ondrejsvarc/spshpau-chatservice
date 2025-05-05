'use strict';

// Page elements (remain mostly the same)
const loadingPage = document.querySelector('#loading-page');
const chatPage = document.querySelector('#chat-page');
const messageForm = document.querySelector('#messageForm');
const messageInput = document.querySelector('#message');
const chatArea = document.querySelector('#chat-messages');
const connectingElement = document.querySelector('.connecting');
const connectedUserFullnameElement = document.querySelector('#connected-user-fullname');
const connectedUsersList = document.getElementById('connectedUsers');
const logout = document.querySelector('#logout');

// --- OIDC Configuration ---
const oidcConfig = {
    authority: 'http://192.168.1.112:8080/realms/SPSHPAU', // Keycloak Issuer URL
    client_id: 'spshpau-rest-api', // The Client ID configured in Keycloak for this app
    redirect_uri: 'http://localhost:8091/', // Where Keycloak redirects back after login
    post_logout_redirect_uri: 'http://localhost:8091/', // Where Keycloak redirects back after logout
    response_type: 'code', // Use Authorization Code Flow
    scope: 'openid profile email', // Standard scopes: openid is required
    automaticSilentRenew: true, // Enable automatic token renewal
    loadUserInfo: true, // Load user profile information
    // Optional: Filter protocol claims from profile
    filterProtocolClaims: true,
};

// --- OIDC User Manager ---
// Check if oidc is loaded
if (typeof oidc === 'undefined') {
    console.error('oidc-client-ts library not found. Please include it in index.html.');
    connectingElement.textContent = 'Error: OIDC Client library not found.';
    connectingElement.style.color = 'red';
    // Stop execution if library is missing
    throw new Error('oidc-client-ts not loaded');
}
const userManager = new oidc.UserManager(oidcConfig);

// --- Global State ---
let username = null; // Keycloak preferred_username (for display)
let userUUID = null; // Keycloak sub claim (UUID, as string initially)
let fullname = null; // Keycloak name
let stompClient = null;
let selectedUserId = null; // THIS WILL NOW STORE THE RECIPIENT'S UUID
let currentUser = null; // Stores the user object from userManager

// --- Authentication Functions ---

async function getUser() {
    currentUser = await userManager.getUser();
    if (currentUser && !currentUser.expired) {
        console.log('User is logged in:', currentUser.profile);
        username = currentUser.profile.preferred_username;
        userUUID = currentUser.profile.sub;
        fullname = currentUser.profile.name || username;
        return currentUser;
    }
    console.log('User not logged in or session expired.');
    userUUID = null; // Clear UUID if not logged in
    return null;
}

async function initializeApp() {
    console.log("initializeApp: Starting..."); // ADDED
    try {
        if (window.location.href.includes('code=') && window.location.href.includes('state=')) {
            console.log("initializeApp: Detected callback URL..."); // ADDED
            // ... existing callback logic ...
        } else {
            console.log("initializeApp: Not a callback, checking existing user..."); // ADDED
            const user = await getUser(); // Add specific catch here?
            if (user) {
                console.log("initializeApp: Found existing user."); // ADDED
                await setupUIAndConnect(user);
            } else {
                console.log("initializeApp: No existing user, calling login()..."); // ADDED
                await login(); // Add specific catch here?
            }
        }
    } catch (error) {
        // ... existing error handling ...
        console.error("initializeApp: Caught error in main try/catch:", error); // ADDED
    }
    console.log("initializeApp: Ending."); // ADDED (Should ideally not be reached if login() redirects)
}

async function login() {
    console.log("login: Starting signinRedirect..."); // ADDED
    try {
        await userManager.signinRedirect({ state: window.location.pathname });
        console.log("login: signinRedirect called (browser should redirect now)."); // ADDED (May not be reached)
    } catch (error) {
        console.error('login: Error during signinRedirect:', error); // ADDED
        // ... existing error handling ...
    }
}

async function logoutUser() {
    console.log('Logging out...');
    const user = await userManager.getUser(); // Get user for id_token_hint and payload

    if (stompClient && stompClient.connected && userUUID) { // Check userUUID
        console.log('Sending disconnect message to backend with payload...');
        // Send payload with necessary identifiers
        const disconnectPayload = {
            userId: userUUID // Use the stored UUID
            // username, firstName, lastName are optional for disconnect if backend only needs ID
        };
        stompClient.send("/app/user.disconnectUser", {}, JSON.stringify(disconnectPayload)); // <-- SEND PAYLOAD

        stompClient.disconnect(() => {
            console.log('STOMP client disconnected.');
            userManager.signoutRedirect({ id_token_hint: user ? user.id_token : undefined })
                .catch(err => console.error("Error during signoutRedirect:", err));
        });
    } else {
        userManager.signoutRedirect({ id_token_hint: user ? user.id_token : undefined })
            .catch(err => console.error("Error during signoutRedirect:", err));
    }
    // Clear UI state immediately (optional)
    chatPage.classList.add('hidden');
    loadingPage.classList.remove('hidden');
    connectingElement.textContent = 'Logging out...';
    userUUID = null; // Clear UUID on logout
}

// --- Application Initialization ---

async function initializeApp() {
    try {
        // Check if the current URL is the redirect callback from Keycloak
        if (window.location.href.includes('code=') && window.location.href.includes('state=')) {
            console.log('Handling Keycloak redirect callback...');
            const user = await userManager.signinRedirectCallback();
            console.log('Callback successful, user:', user.profile);
            // Clean the URL (remove code and state params)
            window.history.replaceState({}, document.title, "/");
            await setupUIAndConnect(user);
        } else {
            // Not a callback, check if user is already logged in
            const user = await getUser();
            if (user) {
                await setupUIAndConnect(user);
            } else {
                // No user logged in, start the login process
                await login();
            }
        }
    } catch (error) {
        console.error('Error during application initialization:', error);
        // Handle specific OIDC errors if needed
        if (error.message === 'No state in response' || error.message === 'No matching state found in storage') {
            console.warn('State mismatch error, likely indicates an old callback URL or manual navigation. Attempting login again.');
            await login(); // Try logging in again
        } else if (error.message === 'Token response failed') {
            console.error('Failed to exchange code for token. Check Keycloak client config (secret?) and network.');
            connectingElement.textContent = 'Login failed: Could not get token from Keycloak.';
            connectingElement.style.color = 'red';
        } else {
            connectingElement.textContent = 'Authentication error occurred. Please try again.';
            connectingElement.style.color = 'red';
            // Optionally add a button to manually trigger login again
        }
    }
}

async function setupUIAndConnect(user) {
    console.log('Setting up UI and connecting WebSocket for user:', user.profile.preferred_username);
    currentUser = user; // Store user globally
    username = user.profile.preferred_username;
    userUUID = user.profile.sub; // <-- Store UUID here too
    fullname = user.profile.name || username; // Fallback

    // Update UI
    loadingPage.classList.add('hidden');
    chatPage.classList.remove('hidden');
    connectedUserFullnameElement.textContent = fullname;

    // Connect to WebSocket
    await connectWebSocket();
}


// --- Helper to get valid access token ---
async function getAccessToken() {
    const user = await userManager.getUser();
    if (user && !user.expired) {
        return user.access_token;
    } else {
        // Attempt silent renew or trigger login if token is missing/expired
        console.warn('Access token missing or expired. Attempting login/refresh.');
        // Depending on UX, you might trigger signinSilent() or signinRedirect()
        // For simplicity here, we'll rely on automatic renewal or redirect on next load
        // Returning null signifies failure to get a token *right now*
        // More robust handling might involve explicit silent sign-in here.
        // await userManager.signinSilent(); // Try silent renew explicitly
        // const refreshedUser = await userManager.getUser();
        // if (refreshedUser && !refreshedUser.expired) return refreshedUser.access_token;
        await login(); // Force login if no valid token found
        return null; // Indicate failure to get token immediately
    }
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

    console.log('Connecting to WebSocket with username:', username);
    connectingElement.textContent = 'Connecting to chat...';
    // Show connecting message temporarily if needed
    // loadingPage.classList.remove('hidden');
    // chatPage.classList.add('hidden');

    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    const headers = { 'Authorization': 'Bearer ' + accessToken };

    stompClient.connect(headers, onConnected, onError);
}

// --- WebSocket Callbacks (onConnected, onError - mostly unchanged logic) ---
function onConnected() {
    console.log('WebSocket connected successfully.');
    connectingElement.textContent = ''; // Clear connecting message

    stompClient.subscribe(`/user/${userUUID}/queue/messages`, onMessageReceived);
    stompClient.subscribe(`/topic/presence`, onPresenceUpdate);

    // Register the connected user - SEND PAYLOAD
    if (userUUID && username) { // Ensure we have the necessary info
        const connectPayload = {
            userId: userUUID, // Keycloak 'sub'
            username: username, // Keycloak 'preferred_username'
            firstName: currentUser.profile.given_name, // Optional: Add if available
            lastName: currentUser.profile.family_name // Optional: Add if available
        };
        stompClient.send("/app/user.addUser", {}, JSON.stringify(connectPayload)); // <-- SEND PAYLOAD
        console.log('Sent addUser message with payload:', connectPayload);
    } else {
        console.error("Cannot send addUser message: Missing user UUID or username.");
    }

    // Display initially connected users
    findAndDisplayConnectedUsers().then();
}

function onError(error) {
    // Same error handling as before
    const errorMsg = 'Could not connect to WebSocket server. Please check backend connection and configuration. May also indicate invalid token.';
    console.error(errorMsg, error);
    connectingElement.textContent = errorMsg;
    connectingElement.style.color = 'red';
    loadingPage.classList.remove('hidden');
    chatPage.classList.add('hidden');
}

// --- Presence Handling (User Connect/Disconnect) ---
function onPresenceUpdate(payload) {
    // Same logic as before: Refresh the user list when someone connects or disconnects
    console.log('Presence update received via /user/topic', payload);
    findAndDisplayConnectedUsers();
}

// --- User List Handling (fetch needs updated token retrieval) ---
async function findAndDisplayConnectedUsers() {
    const accessToken = await getAccessToken();
    if (!accessToken) {
        console.error("Cannot fetch users: Failed to get access token.");
        return;
    }
    try {
        console.log('Fetching connected users...');
        const connectedUsersResponse = await fetch('/users', {
            headers: { 'Authorization': 'Bearer ' + accessToken }
        });

        if (!connectedUsersResponse.ok) {
            if (connectedUsersResponse.status === 401) {
                console.warn("Unauthorized fetching users. Token might be expired.");
                await login(); // Re-trigger login on 401
            }
            throw new Error(`HTTP error! status: ${connectedUsersResponse.status}`);
        }

        let connectedUsers = await connectedUsersResponse.json();
        // Filter out the current user based on the UUID
        connectedUsers = connectedUsers.filter(user => user.id !== userUUID); // <-- Use user.id and userUUID

        connectedUsersList.innerHTML = ''; // Clear existing list
        connectedUsers.forEach(user => {
            // Pass the full user object which contains the ID (UUID)
            appendUserElement(user, connectedUsersList);
            // ... (separator logic remains the same) ...
        });
        console.log('Displayed connected users:', connectedUsers.length);
    } catch (error) {
        console.error('Error fetching or displaying connected users:', error);
    }
}

// appendUserElement remains the same as previous version
function appendUserElement(user, list) {
    const listItem = document.createElement('li');
    listItem.classList.add('user-item');
    // Use username for display ID if needed, but store UUID in data attribute
    listItem.id = `user-item-${user.username}`; // Use username for DOM ID if easier for querySelector later
    listItem.dataset.userId = user.id; // <-- STORE UUID HERE
    listItem.dataset.username = user.username; // Store username too if needed

    // ... (img, usernameSpan, receivedMsgs spans remain the same, use user.fullName || user.username) ...
    const userImage = document.createElement('img');
    userImage.src = '../img/user_icon.png';
    userImage.alt = user.fullName || user.username;
    const usernameSpan = document.createElement('span');
    usernameSpan.textContent = user.fullName || user.username;
    const receivedMsgs = document.createElement('span');
    receivedMsgs.textContent = '0';
    receivedMsgs.classList.add('nbr-msg', 'hidden');

    listItem.appendChild(userImage);
    listItem.appendChild(usernameSpan);
    listItem.appendChild(receivedMsgs);
    listItem.addEventListener('click', userItemClick);
    list.appendChild(listItem);
}

// userItemClick remains the same as previous version
function userItemClick(event) {
    document.querySelectorAll('.user-item').forEach(item => item.classList.remove('active'));
    messageForm.classList.remove('hidden');

    const clickedUser = event.currentTarget;
    clickedUser.classList.add('active');

    // Get the UUID from the data attribute
    selectedUserId = clickedUser.dataset.userId; // <-- GET UUID HERE
    const selectedUsername = clickedUser.dataset.username; // Get username for display if needed

    console.log(`Selected user UUID: ${selectedUserId}, Username: ${selectedUsername}`);

    // Fetch and display chat history using UUIDs
    fetchAndDisplayUserChat().then();

    // Clear notification count (querying by dataset might be needed if ID changed)
    // Querying the clicked element directly is safer:
    const nbrMsg = clickedUser.querySelector('.nbr-msg');
    nbrMsg.classList.add('hidden');
    nbrMsg.textContent = '0';
}

// --- Chat Message Handling (fetch needs updated token retrieval) ---
async function fetchAndDisplayUserChat() {
    if (!selectedUserId || !userUUID) {
        console.error("Cannot fetch chat: Missing selected user UUID or current user UUID.");
        chatArea.innerHTML = '<p>Error fetching chat history.</p>';
        return;
    }
    const accessToken = await getAccessToken();
    if (!accessToken) {
        console.error("Cannot fetch chat: Failed to get access token.");
        chatArea.innerHTML = '<p>Authentication error. Cannot load chat.</p>';
        return;
    }

    try {
        console.log(`Workspaceing chat between ${userUUID} and ${selectedUserId}`);
        // Use UUIDs in the REST URL
        const userChatResponse = await fetch(`/messages/${userUUID}/${selectedUserId}`, {
            headers: { 'Authorization': 'Bearer ' + accessToken }
        });

        if (!userChatResponse.ok) {
            if (userChatResponse.status === 401) {
                console.warn("Unauthorized fetching chat history. Token might be expired.");
                await login(); // Re-trigger login
            }
            throw new Error(`HTTP error! status: ${userChatResponse.status}`);
        }
        const userChat = await userChatResponse.json();
        chatArea.innerHTML = '';
        if (userChat.length === 0) {
            // Optional: Get selected username for display message
            const selectedUserElement = connectedUsersList.querySelector(`[data-user-id="${selectedUserId}"]`);
            const selectedUsernameDisplay = selectedUserElement ? selectedUserElement.dataset.username : selectedUserId;
            chatArea.innerHTML = `<p>No messages with ${selectedUsernameDisplay} yet.</p>`;
        } else {
            // Display message - assumes chat.senderId from backend IS the UUID
            userChat.forEach(chat => displayMessage(chat.senderId, chat.content));
        }
        chatArea.scrollTop = chatArea.scrollHeight;
        console.log(`Displayed ${userChat.length} messages.`);
    } catch(error) {
        console.error('Error fetching chat history:', error);
        chatArea.innerHTML = '<p>Could not load chat history.</p>';
    }
}

// sendMessage remains the same (uses global username, selectedUserId)
function sendMessage(event) {
    event.preventDefault();
    const messageContent = messageInput.value.trim();
    // Ensure selectedUserId is a UUID and userUUID is set
    if (messageContent && stompClient && stompClient.connected && selectedUserId && userUUID) {
        const chatMessage = {
            senderId: userUUID,       // Current user's UUID
            recipientId: selectedUserId, // Selected user's UUID
            content: messageContent,
            timestamp: new Date()
        };
        console.log('Sending chat message via STOMP:', chatMessage);
        // Assumes backend /app/chat handler expects UUIDs in ChatMessage payload
        stompClient.send("/app/chat", {}, JSON.stringify(chatMessage));

        // Display message locally using UUID
        displayMessage(userUUID, messageContent);
        messageInput.value = '';
        chatArea.scrollTop = chatArea.scrollHeight;
    } else {
        if (!stompClient || !stompClient.connected) console.error("Cannot send message: WebSocket not connected.");
        if (!selectedUserId) console.error("Cannot send message: No recipient selected.");
        if (!username) console.error("Cannot send message: Current user not identified.");
    }
}

// onMessageReceived remains the same as previous version
function onMessageReceived(payload) {
    console.log('Message received via WebSocket', payload);
    const message = JSON.parse(payload.body); // Assumes message.senderId IS UUID

    // Compare received UUID with selected UUID
    if (selectedUserId && selectedUserId === message.senderId) {
        displayMessage(message.senderId, message.content);
        chatArea.scrollTop = chatArea.scrollHeight;
    } else {
        // Find user item by data attribute containing the UUID
        console.log(`Notification: New message from ${message.senderId}`);
        const notifiedUserElement = connectedUsersList.querySelector(`[data-user-id="${message.senderId}"]`);

        if (notifiedUserElement) {
            const nbrMsg = notifiedUserElement.querySelector('.nbr-msg');
            if (nbrMsg) {
                nbrMsg.classList.remove('hidden');
                nbrMsg.textContent = isNaN(parseInt(nbrMsg.textContent)) ? '1' : (parseInt(nbrMsg.textContent) + 1).toString();
            }
        } else {
            console.warn(`Received message from unknown or offline user: ${message.senderId}`);
            findAndDisplayConnectedUsers(); // Refresh user list if sender unknown
        }
    }
}

// displayMessage remains the same as previous version
function displayMessage(senderIdUUID, content) {
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('message');

    // Compare with current user's UUID
    if (senderIdUUID === userUUID) {
        messageContainer.classList.add('sender');
    } else {
        messageContainer.classList.add('receiver');
    }
    const message = document.createElement('p');
    message.textContent = content;
    messageContainer.appendChild(message);
    chatArea.appendChild(messageContainer);
    chatArea.scrollTop = chatArea.scrollHeight;
}

// --- Event Listeners ---
messageForm.addEventListener('submit', sendMessage, true);
logout.addEventListener('click', logoutUser, true); // Use new logout function

// --- Application

console.log("main.js: Script loaded, calling initializeApp...");
initializeApp();