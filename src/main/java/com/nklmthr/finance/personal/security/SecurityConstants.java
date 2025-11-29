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
        "/prediction-rules",
        "/accounts",
        "/category-spends",
        "/balance-sheet",
        "/categories",
        "/institutions",
        "/account-types",
        "/uploaded-statements",
        "/gmail/oauth/callback/**",
        "/error",
        "/favicon.ico",
        "/manifest.json",
        "/logo192.png",
        "/logo512.png"
    };

    // Optional: List form for use in custom filters
    public static final List<String> WHITELIST = List.of(WHITELIST_ARRAY);
}
