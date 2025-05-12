package com.spshpau.chatservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtChannelInterceptor jwtChannelInterceptor;
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");

        registry.setApplicationDestinationPrefixes("/app");

        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173", "http://192.168.1.121:5173")
                .withSockJS()
                .setSessionCookieNeeded(false);
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(new ObjectMapper());
        converter.setContentTypeResolver(resolver);

        messageConverters.add(converter);

        return false;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                Map<String, Object> headers = message.getHeaders();
                Object destination = headers.get(SimpMessageHeaderAccessor.DESTINATION_HEADER);
                Object sessionId = headers.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
                Object messageType = headers.get(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER); // STOMP command or MESSAGE
                Object userHeader = headers.get(SimpMessageHeaderAccessor.USER_HEADER);
                String userName = "null";

                if (userHeader instanceof org.springframework.security.core.Authentication) {
                    userName = ((org.springframework.security.core.Authentication) userHeader).getName();
                } else if (userHeader != null) {
                    userName = userHeader.toString();
                }

                log.info(">>>> OUTBOUND MSG (RAW): Type: {}, Dest: {}, SessionId: {}, User: {}",
                        messageType, destination, sessionId, userName);

                // Additional detailed logging with StompHeaderAccessor
                try {
                    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                    String detailedUser = "null";
                    if (accessor.getUser() != null) {
                        detailedUser = accessor.getUser().getName();
                    }
                    // For server-to-client MESSAGE frames, accessor.getCommand() will be null.
                    // accessor.getMessageType() gives the STOMP command for client-to-server frames,
                    // or SimpMessageType.MESSAGE for server-to-client data.
                    log.info(">>>> OUTBOUND MSG (STOMP WRAPPED): Dest: {}, SessionId: {}, User: {}, MessageType: {}, STOMP Command: {}, NativeHeaders: {}",
                            accessor.getDestination(),
                            accessor.getSessionId(),
                            detailedUser,
                            accessor.getMessageType(), // More specific than trying to get command for outbound
                            accessor.getCommand(),    // Will often be null for server-to-client
                            accessor.toNativeHeaderMap()
                    );
                } catch (Exception e) {
                    log.error("Error wrapping outbound message with StompHeaderAccessor. Raw Dest: {}, Raw SessionId: {}", destination, sessionId, e);
                }
                return message;
            }
        });
    }
}
