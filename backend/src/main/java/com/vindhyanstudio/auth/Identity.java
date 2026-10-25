package com.vindhyanstudio.auth;

import com.vindhyanstudio.common.ApiException;

/** A normalised login identifier: an email address or a 10-digit Indian mobile number. */
public record Identity(Type type, String value) {

    public enum Type { EMAIL, PHONE }

    public static Identity parse(String raw) {
        if (raw == null || raw.isBlank()) throw ApiException.badRequest("Enter your mobile number or email");
        String s = raw.trim();
        if (s.contains("@")) {
            s = s.toLowerCase();
            if (!s.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw ApiException.badRequest("Enter a valid email address");
            return new Identity(Type.EMAIL, s);
        }
        String digits = s.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (!digits.matches("[6-9][0-9]{9}")) throw ApiException.badRequest("Enter a valid 10-digit mobile number");
        return new Identity(Type.PHONE, digits);
    }
}
