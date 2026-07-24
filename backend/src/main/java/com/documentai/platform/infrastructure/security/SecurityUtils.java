package com.documentai.platform.infrastructure.security;

import com.documentai.platform.exception.UnauthorizedAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedAccessException("No authenticated user in context");
        }
        return principal;
    }

    public static UUID currentWorkspaceId() {
        return currentUser().getWorkspaceId();
    }

    public static UUID currentUserId() {
        return currentUser().getUserId();
    }

    public static WorkspacePrincipal currentWorkspacePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof WorkspacePrincipal principal)) {
            throw new UnauthorizedAccessException("No authenticated workspace agent in context");
        }
        return principal;
    }
}
