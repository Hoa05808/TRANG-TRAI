package vn.farmcontrol.notify;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "push_token")
public class PushToken {

    public enum Platform { ANDROID, IOS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PushToken() {
    }

    public PushToken(Long userId, String token, Platform platform) {
        this.userId = userId;
        this.token = token;
        this.platform = platform;
        this.createdAt = this.lastSeenAt = Instant.now();
    }

    public void touch(Long userId, Platform platform) {
        this.userId = userId;
        this.platform = platform;
        this.lastSeenAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getToken() { return token; }
    public Platform getPlatform() { return platform; }
}
