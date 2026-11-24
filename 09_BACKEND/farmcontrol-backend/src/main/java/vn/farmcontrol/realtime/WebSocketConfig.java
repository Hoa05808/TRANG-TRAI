package vn.farmcontrol.realtime;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP endpoint: ws://host:8080/ws
 * Client gửi khung CONNECT kèm header  Authorization: Bearer <accessToken>
 * rồi SUBSCRIBE /topic/dashboard, /topic/alerts, /topic/commands.
 * Client không được SEND (mọi lệnh điều khiển đi qua REST để kiểm tra quyền và ghi lịch sử).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtConverter;

    public WebSocketConfig(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtConverter = jwtConverter;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc == null || acc.getCommand() == null) return message;
                StompCommand cmd = acc.getCommand();
                if (cmd == StompCommand.CONNECT) {
                    acc.setUser(authenticate(acc.getNativeHeader("Authorization")));
                } else if (cmd == StompCommand.SEND) {
                    throw new MessageDeliveryException("Không hỗ trợ gửi qua WebSocket, hãy dùng REST API");
                } else if (cmd == StompCommand.SUBSCRIBE) {
                    if (acc.getUser() == null) throw new MessageDeliveryException("Chưa xác thực");
                    String dest = acc.getDestination();
                    if (dest == null || !dest.startsWith("/topic/")) {
                        throw new MessageDeliveryException("Kênh không hợp lệ");
                    }
                }
                return message;
            }
        });
    }

    private Authentication authenticate(List<String> headers) {
        if (headers == null || headers.isEmpty() || !headers.getFirst().startsWith("Bearer ")) {
            throw new MessageDeliveryException("Thiếu header Authorization: Bearer <token>");
        }
        try {
            return jwtConverter.convert(jwtDecoder.decode(headers.getFirst().substring(7)));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Token không hợp lệ hoặc đã hết hạn");
        }
    }
}
