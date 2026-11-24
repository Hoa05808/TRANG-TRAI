package vn.farmcontrol.auth;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import vn.farmcontrol.common.ApiException;
import vn.farmcontrol.user.Role;

/** Người dùng đang đăng nhập, lấy từ JWT. Khai báo làm tham số controller để dùng. */
public record CurrentUser(Long id, String username, Role role) {

    public static CurrentUser from(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new CurrentUser(Long.valueOf(jwt.getSubject()),
                    jwt.getClaimAsString(TokenService.CLAIM_USERNAME),
                    Role.valueOf(jwt.getClaimAsStringList(TokenService.CLAIM_ROLES).getFirst()));
        }
        throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập");
    }

    public static class ArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return CurrentUser.from(SecurityContextHolder.getContext().getAuthentication());
        }
    }
}
