package com.spshpau.chatservice.controller;


import org.springframework.http.ResponseEntity;

public interface UtilController {

    /**
     * A simple health check endpoint.
     *
     * @return A ResponseEntity containing the string "Pong!".
     * Example Response (200 OK):
     * <pre>{@code
     * "Pong!"
     * }</pre>
     */
    ResponseEntity<String> ping();

    /**
     * An endpoint to verify if the provided authentication token is valid.
     * Requires a valid JWT Bearer token in the Authorization header.
     *
     * @return A ResponseEntity containing a success message if authentication is valid.
     * Example Response (200 OK):
     * <pre>{@code
     * "You have sent a valid authentication token!"
     * }</pre>
     * Example Response (401 Unauthorized if token is missing or invalid).
     */
    ResponseEntity<String> auth();
}
