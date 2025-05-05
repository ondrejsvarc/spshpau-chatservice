package com.spshpau.chatservice.config;

import com.spshpau.chatservice.JwtAuthConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthConverter jwtAuthConverter;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            log.debug("CONNECT headers: {}", accessor.toNativeHeaderMap());

            String token = null;
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                } else { log.warn("Authorization header found but no Bearer prefix: {}", authHeader); }
            } else { log.warn("No Authorization header found in CONNECT"); }

            if (token == null) {
                log.warn("No token found, rejecting connection.");
                return null; // Reject connection
            }

            try {
                Jwt jwt = jwtDecoder.decode(token);
                AbstractAuthenticationToken authentication = jwtAuthConverter.convert(jwt);

                if (authentication != null) {
                    accessor.setUser(authentication);
                    log.info("WebSocket CONNECT authenticated for principal name: {}", authentication.getName());
                } else {
                    log.warn("JwtAuthConverter returned null authentication for token.");
                    return null; // Reject if converter fails
                }

            } catch (JwtException ex) {
                log.warn("JWT validation failed during CONNECT: {}", ex.getMessage(), ex); // Log exception details
                return null; // Reject connection
            }
        }
        return message;
    }
}
