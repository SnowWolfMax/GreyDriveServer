package com.greydrive.security;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public static long id() {
        return require().id();
    }

    public static Long idOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return user.id();
        }
        return null;
    }
}
