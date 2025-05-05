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
let username = null; // Keycloak preferred_username
let fullname = null; // Keycloak name
let stompClient = null;
let selectedUserId = null;
let currentUser = null; // Stores the user object from userManager

// --- Authentication Functions ---

async function getUser() {
    currentUser = await userManager.getUser();
    if (currentUser && !currentUser.expired) {
        console.log('User is logged in:', currentUser.profile);
        username = currentUser.profile.preferred_username;
        fullname = currentUser.profile.name || username; // Fallback
        return currentUser;
    }
    console.log('User not logged in or session expired.');
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
    const user = await userManager.getUser(); // Get user for id_token_hint

    if (stompClient && stompClient.connected) {
        console.log('Sending disconnect message to backend...');
        stompClient.send("/app/user.disconnectUser", {});
        stompClient.disconnect(() => {
            console.log('STOMP client disconnected.');
            // Initiate Keycloak logout *after* STOMP disconnect if needed
            userManager.signoutRedirect({ id_token_hint: user ? user.id_token : undefined })
                .catch(err => console.error("Error during signoutRedirect:", err));
        });
    } else {
        // If STOMP wasn't connected, just log out from Keycloak
        userManager.signoutRedirect({ id_token_hint: user ? user.id_token : undefined })
            .catch(err => console.error("Error during signoutRedirect:", err));
    }
    // Clear UI state immediately (optional)
    chatPage.classList.add('hidden');
    loadingPage.classList.remove('hidden');
    connectingElement.textContent = 'Logging out...';
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

    // Subscribe to user-specific messages
    stompClient.subscribe(`/user/${username}/queue/messages`, onMessageReceived);
    // Subscribe to public user notifications (user connect/disconnect broadcasts)
    stompClient.subscribe(`/user/topic`, onPresenceUpdate); // Use /user/topic now

    // Register the connected user (inform backend - payload ignored)
    stompClient.send("/app/user.addUser", {});

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
        // Filter out the current user based on the username
        connectedUsers = connectedUsers.filter(user => user.username !== username);
        connectedUsersList.innerHTML = ''; // Clear existing list
        connectedUsers.forEach(user => {
            appendUserElement(user, connectedUsersList);
            if (connectedUsers.indexOf(user) < connectedUsers.length - 1) {
                const separator = document.createElement('li');
                separator.classList.add('separator');
                connectedUsersList.appendChild(separator);
            }
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
    listItem.id = user.username; // Use username as the unique ID
    const userImage = document.createElement('img');
    userImage.src = '../img/user_icon.png'; // Default icon
    userImage.alt = user.fullName || user.username;
    const usernameSpan = document.createElement('span');
    usernameSpan.textContent = user.fullName || user.username; // Display fullname or username
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
    selectedUserId = clickedUser.getAttribute('id'); // Username is the ID
    console.log(`Selected user: ${selectedUserId}`);
    fetchAndDisplayUserChat().then(); // Fetch history for selected user
    const nbrMsg = clickedUser.querySelector('.nbr-msg');
    nbrMsg.classList.add('hidden');
    nbrMsg.textContent = '0';
}

// --- Chat Message Handling (fetch needs updated token retrieval) ---
async function fetchAndDisplayUserChat() {
    if (!selectedUserId || !username) {
        console.error("Cannot fetch chat: Missing selected user or current user.");
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
        console.log(`Workspaceing chat between ${username} and ${selectedUserId}`);
        const userChatResponse = await fetch(`/messages/<span class="math-inline">\{username\}/</span>{selectedUserId}`, {
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
        chatArea.innerHTML = ''; // Clear previous messages
        if (userChat.length === 0) {
            chatArea.innerHTML = `<p>No messages with ${selectedUserId} yet.</p>`;
        } else {
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
    if (messageContent && stompClient && stompClient.connected && selectedUserId && username) {
        const chatMessage = {
            senderId: username,
            recipientId: selectedUserId,
            content: messageContent,
            timestamp: new Date()
        };
        console.log('Sending message via STOMP:', chatMessage);
        stompClient.send("/app/chat", {}, JSON.stringify(chatMessage));
        displayMessage(username, messageContent); // Display sent message locally
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
    const message = JSON.parse(payload.body);
    if (selectedUserId && selectedUserId === message.senderId) {
        displayMessage(message.senderId, message.content);
        chatArea.scrollTop = chatArea.scrollHeight;
    } else {
        console.log(`Notification: New message from ${message.senderId}`);
        const notifiedUser = document.querySelector(`#${message.senderId}`);
        if (notifiedUser) {
            const nbrMsg = notifiedUser.querySelector('.nbr-msg');
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
function displayMessage(senderId, content) {
    const messageContainer = document.createElement('div');
    messageContainer.classList.add('message');
    if (senderId === username) { // Compare with global username
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