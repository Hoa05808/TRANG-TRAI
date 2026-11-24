package vn.farmcontrol.auth;

import jakarta.validation.constraints.NotBlank;
import vn.farmcontrol.user.UserDtos.UserResponse;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType,
                                long expiresInSeconds, UserResponse user) {
    }
}
