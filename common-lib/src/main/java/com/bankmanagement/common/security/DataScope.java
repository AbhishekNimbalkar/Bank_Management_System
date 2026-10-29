package com.bankmanagement.common.security;

import com.bankmanagement.common.exception.BusinessException;

import java.util.UUID;

/**
 * Which records of a tenant the caller may see: the whole tenant (SSA/SA/HR), one branch (BM)
 * or only their own (everyone else).
 */
public record DataScope(Level level, UUID branchId, UUID userId) {

    public enum Level { TENANT, BRANCH, SELF }

    public static DataScope of(AuthenticatedUser user) {
        String role = user.role();
        if (Role.SSA.name().equals(role) || Role.SA.name().equals(role) || Role.HR.name().equals(role)) {
            return new DataScope(Level.TENANT, null, user.userId());
        }
        if (Role.BM.name().equals(role)) {
            return new DataScope(Level.BRANCH, user.branchId(), user.userId());
        }
        return new DataScope(Level.SELF, user.branchId(), user.userId());
    }

    public boolean includes(UUID recordBranchId, UUID recordUserId) {
        return switch (level) {
            case TENANT -> true;
            case BRANCH -> branchId != null && branchId.equals(recordBranchId);
            case SELF -> userId.equals(recordUserId);
        };
    }

    /**
     * Approver rule shared by leaves, corrections and timesheets: never your own item, the item must be
     * in scope, and unless you are SA/SSA the subject must rank strictly below you.
     */
    public static void assertCanDecide(AuthenticatedUser approver, UUID subjectUserId, UUID subjectBranchId,
                                       String subjectRole) {
        if (approver.userId().equals(subjectUserId)) {
            throw BusinessException.forbidden("You cannot decide your own request.");
        }
        if (!of(approver).includes(subjectBranchId, subjectUserId)) {
            throw BusinessException.forbidden("This request is outside your scope.");
        }
        boolean topAdmin = Role.SA.name().equals(approver.role()) || approver.isPlatformOwner();
        if (!topAdmin && Role.rankOf(subjectRole) >= approver.rank()) {
            throw BusinessException.forbidden(
                    "Only a more senior role can decide this request.");
        }
    }
}
