package com.bankmanagement.common.security;

/** Names of the custom claims carried in Bank Management System access tokens. */
public final class TokenClaims {

    public static final String TENANT = "tid";
    public static final String ROLE = "role";
    public static final String NAME = "name";
    public static final String USERNAME = "preferred_username";
    public static final String EMPLOYEE_ID = "eid";
    public static final String EMPLOYEE_CODE = "ecode";
    public static final String BRANCH_ID = "bid";
    public static final String PERMISSIONS = "perms";
    public static final String IMPERSONATOR = "imp";

    public static final String AUDIENCE = "bank-management";

    private TokenClaims() {
    }
}
