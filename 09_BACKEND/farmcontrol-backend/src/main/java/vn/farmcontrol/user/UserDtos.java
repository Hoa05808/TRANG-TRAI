package vn.farmcontrol.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(Long id, String username, String fullName, Role role, boolean enabled,
                               Instant createdAt) {
        public static UserResponse of(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Chỉ dùng chữ, số và các ký tự . _ -")
            String username,
            @NotBlank @Size(min = 8, max = 100, message = "Mật khẩu tối thiểu 8 ký tự") String password,
            @Size(max = 100) String fullName,
            @NotNull Role role) {
    }

    public record UpdateUserRequest(@Size(max = 100) String fullName, Role role, Boolean enabled) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 100, message = "Mật khẩu tối thiểu 8 ký tự") String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100, message = "Mật khẩu tối thiểu 8 ký tự") String newPassword) {
    }
}
