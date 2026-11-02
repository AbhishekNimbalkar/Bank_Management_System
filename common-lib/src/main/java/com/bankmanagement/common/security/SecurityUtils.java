package com.bankmanagement.common.security;

import com.bankmanagement.common.exception.BusinessException;
import com.bankmanagement.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.regex.Pattern;

/** Static access to the current caller and the tenant a request acts on. */
public final class SecurityUtils {

    /** Header a platform owner uses to act inside one tenant. Ignored for everyone else. */
    public static final String TENANT_HEADER = "X-Tenant-Code";

    private static final Pattern TENANT_CODE = Pattern.compile("^[a-z][a-z0-9]{2,19}$");

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> findCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser currentUser() {
        return findCurrentUser().orElseThrow(() ->
                new BusinessException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    /**
     * Tenant the current request operates on. Normal users are always pinned to the tenant in their
     * token; the platform owner may target a tenant with the {@value #TENANT_HEADER} header.
     */
    public static String currentTenant() {
        AuthenticatedUser user = currentUser();
        if (user.isPlatformOwner()) {
            String requested = currentRequest()
                    .map(r -> r.getHeader(TENANT_HEADER))
                    .filter(h -> !h.isBlank())
                    .orElse(null);
            if (requested != null) {
                if (!TENANT_CODE.matcher(requested).matches()) {
                    throw BusinessException.validation("Invalid tenant code header.", TENANT_HEADER);
                }
                return requested;
            }
        }
        return user.tenantCode();
    }

    public static void requirePermission(String module, char action) {
        if (!currentUser().hasPermission(module, action)) {
            throw BusinessException.forbidden("You are not allowed to perform this action on " + module + ".");
        }
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    public static boolean isValidTenantCode(String code) {
        return code != null && TENANT_CODE.matcher(code).matches();
    }
}
