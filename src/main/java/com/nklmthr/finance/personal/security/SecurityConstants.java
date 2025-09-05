package com.nklmthr.finance.personal.security;

import java.util.List;

public class SecurityConstants {
    // Ant-style patterns (Spring Security compatible)
    public static final String[] WHITELIST_ARRAY = {
        "/api/auth/login/**",
        "/api/signup",
        "/login",
        "/signup",
        "/",
        "/index.html",
        "/static/**",
        "/css/**",
        "/js/**",
        "/images/**",
        "/profile",
        "/transactions",
        "/accounts",
        "/category-spends",
        "/balance-sheet",
        "/categories",
        "/institutions",
        "/account-types",
        "/gmail/oauth/callback/**",
        "/error" 
    };

    // Optional: List form for use in custom filters
    public static final List<String> WHITELIST = List.of(WHITELIST_ARRAY);
}
