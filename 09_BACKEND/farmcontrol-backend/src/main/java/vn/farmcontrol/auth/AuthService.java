package vn.farmcontrol.auth;

import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farmcontrol.auth.AuthDtos.TokenResponse;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.config.FarmProperties;
import vn.farmcontrol.user.User;
import vn.farmcontrol.user.UserDtos.UserResponse;
import vn.farmcontrol.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptGuard loginGuard;
    private final Duration refreshTtl;
    /** Hash giả để so sánh khi sai tên đăng nhập, tránh lộ tài khoản qua thời gian phản hồi. */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokenService, LoginAttemptGuard loginGuard, FarmProperties props) {
        this.loginGuard = loginGuard;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTtl = Duration.ofDays(props.security().refreshTokenDays());
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public TokenResponse login(String username, String password) {
        loginGuard.checkAllowed(username);
        User user = users.findByUsername(username).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean ok = passwordEncoder.matches(password, hash);
        if (user == null || !ok) {
            loginGuard.onFailure(username);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Sai tên đăng nhập hoặc mật khẩu");
        }
        loginGuard.onSuccess(username);
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị khóa");
        }
        return issue(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(TokenService.sha256(refreshToken))
                .filter(t -> t.isUsable(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
        User user = users.findById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "Phiên đăng nhập không hợp lệ"));
        stored.revoke(); // xoay vòng: mỗi refresh token chỉ dùng được một lần
        return issue(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(TokenService.sha256(refreshToken)).ifPresent(RefreshToken::revoke);
    }

    private TokenResponse issue(User user) {
        String raw = TokenService.newOpaqueToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenService.sha256(raw), Instant.now().plus(refreshTtl)));
        return new TokenResponse(tokenService.createAccessToken(user), raw, "Bearer",
                tokenService.accessTtl().toSeconds(), UserResponse.of(user));
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldTokens() {
        refreshTokens.deleteExpired(Instant.now());
    }
}
