# Chat Service (chatservice)

## Description

The Chat Service is a Spring Boot microservice designed to facilitate real-time one-to-one chat functionalities. It manages user presence, chat rooms, and message persistence and delivery. It is part of the larger SPSHPAU project ecosystem and integrates with a User Service (for user connection details), a Spring Cloud Config Server, and a Eureka server for service discovery. The service is secured using OAuth2/JWT for both HTTP and WebSocket communications.

## Features

* **Real-time Chat**:
    * Supports one-to-one messaging between users.
    * Uses WebSockets (via STOMP over SockJS) for real-time communication.
* **User Presence**:
    * Tracks user online/offline status.
    * Broadcasts presence updates to connected clients.
* **Chat Room Management**:
    * Dynamically creates and retrieves chat rooms between two users.
    * Ensures a unique, shared `chatId` for each pair of users.
* **Message Management**:
    * Persists chat messages to MongoDB.
    * Handles message sending and routes messages to the appropriate recipient queue.
    * Tracks message status: `SENT`, `DELIVERED`, `READ`.
    * Provides real-time updates on message status changes to the sender.
* **Chat Summaries**:
    * Lists a user's active chats with partners.
    * Displays unread message counts for each chat.
* **User Interaction**:
    * Fetches user's connections from an external User Service via a Feign client (`UserClient`) to establish potential chat partners.
    * Persists basic user information (ID, username, name, status) locally in its own MongoDB database for quick access related to chat operations.
* **Security**:
    * HTTP endpoints are secured using OAuth2 and JWT Bearer tokens.
    * WebSocket connections (`/ws`) are authenticated using JWT Bearer tokens passed in STOMP CONNECT headers.
    * A `JwtAuthConverter` is used for extracting user details and roles from the JWT.
* **Microservice Architecture**:
    * Registers with a Eureka server for service discovery.
    * Pulls its configuration from a Spring Cloud Config Server.

## Technologies Used

* **Backend**:
    * Java 17
    * Spring Boot 3.4.5 (as per `pom.xml`)
    * Spring MVC (for REST APIs)
    * Spring WebSocket (STOMP over SockJS)
    * Spring Data MongoDB
    * Spring Security (OAuth2 Resource Server, JWT)
    * Spring Cloud:
        * Config Client
        * Netflix Eureka Client
        * OpenFeign
* **Database**:
    * MongoDB
* **Build & Dependency Management**:
    * Apache Maven
* **Utilities**:
    * Lombok
* **Frontend (Client-side for interaction) - For testing purposes only!**:
    * HTML, CSS, JavaScript
    * SockJS-client, Stomp.js
    * oidc-client-ts (for OIDC/OAuth2 authentication in the frontend)
* **Testing**:
    * JUnit 5
    * Mockito
* **Containerization**:
    * Docker

## Prerequisites

Before running this service, ensure you have the following set up and running:

* **Java Development Kit (JDK)**: Version 17 or higher.
* **Apache Maven**: Version 3.6.x or higher.
* **MongoDB**: An accessible instance. A `docker-compose.yml` is provided for easy setup.
* **Spring Cloud Config Server**: Running and configured to serve the `chatservice` application's properties. (Default expected at `http://localhost:8888`)
* **Spring Cloud Netflix Eureka Server**: Running for service registration and discovery.
* **OAuth2 Identity Provider (IdP)**: Such as Keycloak, configured to issue JWTs. The service and frontend need to be configured with the IdP's details.
* **User Service**: The dependent `userservice` (as defined in `UserClient`) must be running and accessible. This service is used to fetch the user's connections who can be chat partners.
* **Docker**: (Optional) If you plan to run the service and MongoDB using Docker.

## Configuration

The service is configured primarily through `application.yml` (or `application.properties`) which is expected to be largely supplied by the Spring Cloud Config Server.

Key configuration properties include:

* **Application Name**:
    ```yaml
    spring:
      application:
        name: chatservice
    ```
* **Config Server Import**:
    ```yaml
    spring:
      config:
        import: optional:configserver:http://localhost:8888 # Or your config server URL
    ```
* **MongoDB Connection**: (Typically provided by Config Server)
    ```yaml
    # Example - actual values from Config Server
    spring:
      data:
        mongodb:
          uri: mongodb://spshpau:admin@localhost:27017/chatservice_db # Example, adjust as needed
          # Other properties like host, port, username, password, database can be used
    ```
* **Eureka Client**: (Typically provided by Config Server)
    ```yaml
    # Example - actual values from Config Server
    eureka:
      client:
        serviceUrl:
          defaultZone: http://localhost:8761/eureka/
      instance:
        preferIpAddress: true
    ```
* **OAuth2 Resource Server (Keycloak example)**: (Typically provided by Config Server)
    ```yaml
    # Example - actual values from Config Server
    spring:
      security:
        oauth2:
          resourceserver:
            jwt:
              issuer-uri: [http://192.168.1.112:8080/realms/SPSHPAU](http://192.168.1.112:8080/realms/SPSHPAU) # Match your IdP
              # jwk-set-uri: ${spring.security.oauth2.resourceserver.jwt.issuer-uri}/protocol/openid-connect/certs
    ```
* **JWT Authentication Converter**: (Typically provided by Config Server)
    ```yaml
    # Example - actual values from Config Server
    jwt:
      auth:
        converter:
          resource-id: spshpau-rest-api # Client ID in Keycloak that this service validates against for roles
          principle-attribute: sub # 'sub' (subject) is typically used for user ID
    ```
* **UserClient URL Configuration**: (Typically provided by Config Server)
    ```yaml
    # Example - actual value from Config Server
    application:
      config: # Note: 'config' might be a typo in the example, consider 'cofig' as per UserClient or a more descriptive path
        userclienturl: http://localhost:8090 # URL of the userservice
    ```

Ensure your Config Server is properly set up with a configuration file for `chatservice` (e.g., `chatservice.yml` or `chatservice-default.yml`).

## Building the Service

1.  **Clone the repository** (if applicable).
2.  **Navigate to the project root directory** (where `pom.xml` is located).
3.  **Build the project using Maven**:
    ```bash
    mvn clean package
    ```
    This will compile the code, run tests (unless skipped using `-DskipTests`), and package the application into a JAR file located in the `target/` directory (e.g., `target/chatservice-0.1.1-ALPHA.jar`).

## Running the Service

### Locally with Docker Compose for MongoDB

A `docker-compose.yml` is provided to easily run MongoDB:
```bash
docker-compose up -d mongodb