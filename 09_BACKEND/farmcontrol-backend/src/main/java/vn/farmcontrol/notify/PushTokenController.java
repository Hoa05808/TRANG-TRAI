package vn.farmcontrol.notify;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.auth.CurrentUser;

/** App gọi sau khi đăng nhập để đăng ký token FCM của điện thoại. */
@RestController
@RequestMapping("/api/push-tokens")
public class PushTokenController {

    public record RegisterRequest(@NotBlank @Size(max = 512) String token, @NotNull PushToken.Platform platform) {
    }

    public record UnregisterRequest(@NotBlank String token) {
    }

    private final PushTokenRepository repo;

    public PushTokenController(PushTokenRepository repo) {
        this.repo = repo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void register(@Valid @RequestBody RegisterRequest req, CurrentUser user) {
        repo.findByToken(req.token()).ifPresentOrElse(
                t -> t.touch(user.id(), req.platform()),
                () -> repo.save(new PushToken(user.id(), req.token(), req.platform())));
    }

    @PostMapping("/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@Valid @RequestBody UnregisterRequest req, CurrentUser user) {
        repo.deleteByTokenAndUserId(req.token(), user.id());
    }
}
