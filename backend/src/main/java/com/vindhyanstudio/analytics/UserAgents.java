/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.analytics;

/** Rough device / browser / OS from a User-Agent string. Good enough for charts; no external library. */
final class UserAgents {

    private UserAgents() {}

    record Info(String device, String browser, String os) {}

    static Info parse(String ua) {
        if (ua == null || ua.isBlank()) return new Info("unknown", "unknown", "unknown");
        String u = ua.toLowerCase();
        String device = u.contains("ipad") || u.contains("tablet") || (u.contains("android") && !u.contains("mobile")) ? "tablet"
                : u.contains("mobi") || u.contains("iphone") || u.contains("android") ? "mobile" : "desktop";
        String browser = u.contains("edg/") ? "Edge"
                : u.contains("opr/") || u.contains("opera") ? "Opera"
                : u.contains("samsungbrowser") ? "Samsung Internet"
                : u.contains("instagram") ? "Instagram app"
                : u.contains("fban") || u.contains("fbav") ? "Facebook app"
                : u.contains("crios") || (u.contains("chrome/") && !u.contains("chromium")) ? "Chrome"
                : u.contains("fxios") || u.contains("firefox/") ? "Firefox"
                : u.contains("safari/") ? "Safari" : "Other";
        String os = u.contains("iphone") || u.contains("ipad") || u.contains("ios") ? "iOS"
                : u.contains("android") ? "Android"
                : u.contains("windows") ? "Windows"
                : u.contains("mac os") || u.contains("macintosh") ? "macOS"
                : u.contains("linux") ? "Linux" : "Other";
        return new Info(device, browser, os);
    }
}
