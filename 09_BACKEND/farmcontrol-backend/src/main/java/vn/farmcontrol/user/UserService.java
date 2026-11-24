package vn.farmcontrol.user;

import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.farmcontrol.auth.RefreshTokenRepository;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.user.UserDtos.*;

@Service
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::of).toList();
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("tài khoản"));
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (users.existsByUsername(req.username())) {
            throw ApiException.conflict("USERNAME_TAKEN", "Tên đăng nhập đã tồn tại");
        }
        User u = new User(req.username(), passwordEncoder.encode(req.password()), req.fullName(), req.role());
        return UserResponse.of(users.save(u));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, Long actingUserId) {
        User u = get(id);
        boolean losingOwner = u.getRole() == Role.OWNER && u.isEnabled()
                && ((req.role() != null && req.role() != Role.OWNER) || Boolean.FALSE.equals(req.enabled()));
        if (losingOwner && users.countByRoleAndEnabledTrue(Role.OWNER) <= 1) {
            throw ApiException.conflict("LAST_OWNER", "Phải còn ít nhất một tài khoản chủ trang trại đang hoạt động");
        }
        if (id.equals(actingUserId) && Boolean.FALSE.equals(req.enabled())) {
            throw ApiException.badRequest("SELF_DISABLE", "Không thể tự khóa tài khoản của mình");
        }
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.role() != null) u.setRole(req.role());
        if (req.enabled() != null) {
            u.setEnabled(req.enabled());
            if (!req.enabled()) refreshTokens.revokeAllForUser(u.getId());
        }
        return UserResponse.of(u);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User u = get(id);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(u.getId());
    }

    @Transactional
    public void changeOwnPassword(Long userId, ChangePasswordRequest req) {
        User u = get(userId);
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        refreshTokens.revokeAllForUser(u.getId());
    }
}
