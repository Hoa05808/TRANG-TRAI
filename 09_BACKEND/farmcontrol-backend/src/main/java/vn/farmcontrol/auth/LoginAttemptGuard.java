package vn.farmcontrol.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.farmcontrol.common.ApiException;

/** Khóa tạm tên đăng nhập sau nhiều lần sai mật khẩu liên tiếp (chống dò mật khẩu). */
@Component
public class LoginAttemptGuard {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_TIME = Duration.ofMinutes(5);

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String username) {
        Attempts a = attempts.get(key(username));
        if (a != null && a.lockedUntil() != null && a.lockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Sai mật khẩu quá nhiều lần, vui lòng thử lại sau 5 phút");
        }
    }

    public void onFailure(String username) {
        attempts.compute(key(username), (k, a) -> {
            int failures = (a == null || a.lockedUntil() != null) ? 1 : a.failures() + 1;
            return failures >= MAX_FAILURES
                    ? new Attempts(0, Instant.now().plus(LOCK_TIME))
                    : new Attempts(failures, null);
        });
    }

    public void onSuccess(String username) {
        attempts.remove(key(username));
    }

    private static String key(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
