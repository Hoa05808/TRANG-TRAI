package vn.farmcontrol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import vn.farmcontrol.auth.TokenService;
import vn.farmcontrol.gateway.GatewayEvents.TelemetryReceived;
import vn.farmcontrol.plc.TelemetryMessage;
import vn.farmcontrol.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketIntegrationTest {

    @LocalServerPort int port;
    @Autowired TokenService tokens;
    @Autowired UserRepository users;
    @Autowired ApplicationEventPublisher events;

    private WebSocketStompClient client() {
        WebSocketStompClient c = new WebSocketStompClient(new StandardWebSocketClient());
        c.setMessageConverter(new JacksonJsonMessageConverter());
        return c;
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) connectHeaders.add("Authorization", "Bearer " + token);
        return client().connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void dashboardIsPushedToAuthenticatedSubscribers() throws Exception {
        String token = tokens.createAccessToken(users.findByUsername("owner").orElseThrow());
        StompSession session = connect(token);
        BlockingQueue<Map<?, ?>> received = new LinkedBlockingQueue<>();
        session.subscribe("/topic/dashboard", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<?, ?>) payload);
            }
        });
        Thread.sleep(300); // chờ SUBSCRIBE tới server

        int[] d50 = {301, 700, 280, 500, 500, 1000, 700, 5, 1, 1};
        int[] d100 = {0, 1, 0, 1, 0, 1, 1, 0, 0, 100};
        int[] d200 = {1, 7, 1, 0, 0, 0, 0, 0, 0, 1};
        events.publishEvent(new TelemetryReceived(new TelemetryMessage("gw", 1L, d50, d100, null, d200), Instant.now()));

        Map<?, ?> dash = received.poll(5, TimeUnit.SECONDS);
        assertThat(dash).isNotNull();
        assertThat(dash.get("connection")).isEqualTo("ONLINE");
        assertThat(((Map<?, ?>) dash.get("sensors")).get("coopTemp")).isEqualTo(30.1);
        session.disconnect();
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> connect(null))
                .isInstanceOf(java.util.concurrent.ExecutionException.class);
    }
}
