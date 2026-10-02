package com.cognivault.config;
import com.cognivault.security.StompAuthChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
@Configuration @EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
 private final CogniVaultProperties properties;private final StompAuthChannelInterceptor auth;
 public WebSocketConfig(CogniVaultProperties properties,StompAuthChannelInterceptor auth){this.properties=properties;this.auth=auth;}
 @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/topic","/queue");registry.setApplicationDestinationPrefixes("/app");registry.setUserDestinationPrefix("/user");}
 @Override public void registerStompEndpoints(StompEndpointRegistry registry){String origins=properties.websocket().allowedOrigins();registry.addEndpoint("/ws").setAllowedOriginPatterns((origins==null?"http://localhost:5173":origins).split(","));}
 @Override public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration){registration.interceptors(auth);}
}