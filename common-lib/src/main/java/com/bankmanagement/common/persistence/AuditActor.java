package com.bankmanagement.common.persistence;

import com.bankmanagement.common.security.SecurityUtils;

/** Who to record in created_by / updated_by / deleted_by: the signed-in user's id, else "system". */
public final class AuditActor {

    public static final String SYSTEM = "system";

    private AuditActor() {
    }

    public static String current() {
        return SecurityUtils.findCurrentUser()
                .map(u -> u.userId().toString())
                .orElse(SYSTEM);
    }
}
