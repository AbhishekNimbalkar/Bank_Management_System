package com.bankmanagement.organization.dto;

/** Validation patterns for Indian banking identifiers. */
public final class Patterns {

    public static final String IFSC = "^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String PINCODE = "^[1-9][0-9]{5}$";
    public static final String MOBILE = "^[6-9][0-9]{9}$";
    public static final String USERNAME = "^[a-z0-9][a-z0-9._-]{2,31}$";
    public static final String TENANT_CODE = "^[a-z][a-z0-9]{2,19}$";
    public static final String ACCOUNT_NUMBER = "^[0-9]{9,18}$";
    public static final String ROLE_CODE = "^[A-Z][A-Z0-9]{1,9}$";
    /** Free text without angle brackets (blocks HTML/script injection into UIs and exports). */
    public static final String SAFE_TEXT = "^[^<>]*$";

    private Patterns() {
    }
}
