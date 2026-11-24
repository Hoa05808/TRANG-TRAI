package vn.farmcontrol.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.farmcontrol.auth.TokenService;
import vn.farmcontrol.user.Role;
import vn.farmcontrol.user.User;
import vn.farmcontrol.user.UserRepository;

/** Tạo tài khoản chủ trang trại đầu tiên khi cơ sở dữ liệu còn trống. */
@Component
public class BootstrapOwner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapOwner.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final FarmProperties props;

    public BootstrapOwner(UserRepository users, PasswordEncoder passwordEncoder, FarmProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String username = props.security().bootstrapOwnerUsername();
        String password = props.security().bootstrapOwnerPassword();
        boolean generated = password == null || password.isBlank();
        if (generated) {
            password = TokenService.newOpaqueToken().substring(0, 16);
        }
        users.save(new User(username, passwordEncoder.encode(password), "Chủ trang trại", Role.OWNER));
        if (generated) {
            log.warn("""

                    ==================================================================
                     Đã tạo tài khoản chủ trang trại đầu tiên
                       Tên đăng nhập : {}
                       Mật khẩu      : {}
                     Hãy đăng nhập và đổi mật khẩu ngay. Thông báo này chỉ hiện một lần.
                    ==================================================================""",
                    username, password);
        } else {
            log.info("Đã tạo tài khoản chủ trang trại '{}' từ biến môi trường OWNER_PASSWORD", username);
        }
    }
}
