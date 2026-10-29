package com.bankmanagement.auth.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Out-of-the-box rights per role; tenants override individual cells via the permission matrix. */
public final class DefaultPermissions {

    /** Modules only the platform owner can ever access. */
    public static final Set<String> PLATFORM_MODULES = Set.of("superadmins", "masterCustomers", "masterBanks");

    public static final List<String> MODULES = List.of(
            "superadmins", "masterCustomers", "masterBanks", "dashboard", "banks", "branches", "employees",
            "attendance", "leaves", "policies", "holidays", "plans", "customers", "leaderboard", "timesheets",
            "products", "logs", "permissions", "announcements", "regs", "config");

    public static final List<String> ACTIONS = List.of("C", "R", "U", "D");

    private static final Map<String, Map<String, String>> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("SA", row(
                "dashboard", "R", "banks", "CRUD", "branches", "CRUD", "employees", "CRUD", "attendance", "CRUD",
                "leaves", "CRUD", "policies", "CRUD", "holidays", "CRUD", "plans", "CRUD", "customers", "CRUD",
                "leaderboard", "R", "timesheets", "CRUD", "products", "CRUD", "logs", "R", "permissions", "CRUD",
                "announcements", "CRUD", "regs", "CRUD", "config", "CRUD"));
        DEFAULTS.put("HR", row(
                "dashboard", "R", "banks", "R", "branches", "R", "employees", "CRUD", "attendance", "CRU",
                "leaves", "CRUD", "policies", "CRUD", "holidays", "CRUD", "plans", "CRUD", "customers", "CRUD",
                "leaderboard", "R", "timesheets", "CRUD", "products", "R", "permissions", "R",
                "announcements", "CRUD", "regs", "CRUD", "config", "R"));
        DEFAULTS.put("BM", row(
                "dashboard", "R", "branches", "R", "employees", "CRUD", "attendance", "CRU", "leaves", "CRU",
                "policies", "R", "holidays", "R", "plans", "CR", "customers", "CRUD", "leaderboard", "R",
                "timesheets", "CRU", "products", "R", "permissions", "R", "announcements", "CR", "regs", "CRU",
                "config", "R"));
        DEFAULTS.put("EMP", row(
                "dashboard", "R", "attendance", "CR", "leaves", "CRD", "policies", "R", "holidays", "R",
                "plans", "R", "customers", "CRU", "leaderboard", "R", "timesheets", "CRUD", "products", "R",
                "permissions", "R", "announcements", "R", "regs", "CRD"));
    }

    private DefaultPermissions() {
    }

    /** Default actions for a role on a module ("" when none). Custom roles start with no rights. */
    public static String actions(String role, String module) {
        return DEFAULTS.getOrDefault(role, Map.of()).getOrDefault(module, "");
    }

    public static String normalize(String actions) {
        StringBuilder sb = new StringBuilder();
        for (String a : ACTIONS) {
            if (actions.contains(a)) {
                sb.append(a);
            }
        }
        return sb.toString();
    }

    private static Map<String, String> row(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
