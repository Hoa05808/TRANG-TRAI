package vn.farmcontrol.user;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.auth.CurrentUser;
import vn.farmcontrol.user.UserDtos.*;

@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(CurrentUser current) {
        return UserResponse.of(userService.get(current.id()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(CurrentUser current, @Valid @RequestBody ChangePasswordRequest req) {
        userService.changeOwnPassword(current.id(), req);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    public List<UserResponse> list() {
        return userService.list();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest req) {
        return userService.create(req);
    }

    @PatchMapping("/users/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req, CurrentUser current) {
        return userService.update(id, req, current.id());
    }

    @PostMapping("/users/{id}/password")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(id, req.newPassword());
    }
}
