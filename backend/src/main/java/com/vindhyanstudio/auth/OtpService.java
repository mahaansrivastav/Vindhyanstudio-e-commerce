package com.vindhyanstudio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.notify.NotificationService;

/** Issues and verifies one-time passwords (BRD FR-03: 6 digits, 5 min, 30 s resend, limited attempts). */
@Service
public class OtpService {

    private static final int MAX_REQUESTS_PER_HOUR = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeRepository repo;
    private final NotificationService notifications;
    private final AppProperties.Auth cfg;
    private final com.vindhyanstudio.notify.EmailTemplates templates;

    public OtpService(OtpCodeRepository repo, NotificationService notifications, AppProperties props,
                      com.vindhyanstudio.notify.EmailTemplates templates) {
        this.templates = templates;
        this.repo = repo;
        this.notifications = notifications;
        this.cfg = props.auth();
    }

    /** @return the code itself, so local dev can show it; callers must only expose it when allowed. */
    @Transactional
    public String issue(Identity identity) {
        OffsetDateTime now = OffsetDateTime.now();
        repo.findFirstByIdentityOrderByCreatedAtDesc(identity.value()).ifPresent(last -> {
            long wait = cfg.otpResendSeconds() - Duration.between(last.createdAt, now).toSeconds();
            if (wait > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait " + wait + "s before requesting a new code");
        });
        if (repo.countByIdentityAndCreatedAtAfter(identity.value(), now.minusHours(1)) >= MAX_REQUESTS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested. Try again later.");
        }

        String code = String.format("%0" + cfg.otpLength() + "d", RANDOM.nextInt((int) Math.pow(10, cfg.otpLength())));
        OtpCode otp = new OtpCode();
        otp.identity = identity.value();
        otp.codeHash = hash(identity.value(), code);
        otp.expiresAt = now.plusSeconds(cfg.otpTtlSeconds());
        repo.save(otp);

        String text = code + " is your Vindhyan Studio login code. It expires in " + (cfg.otpTtlSeconds() / 60) + " minutes.";
        if (identity.type() == Identity.Type.EMAIL) {
            notifications.email(identity.value(), templates.otp(code, cfg.otpTtlSeconds() / 60), null);
        } else {
            notifications.sms(identity.value(), text, null);
        }
        return code;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verify(Identity identity, String code) {
        OtpCode otp = repo.findFirstByIdentityOrderByCreatedAtDesc(identity.value())
                .filter(o -> !o.consumed && o.expiresAt.isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> ApiException.badRequest("Code expired. Please request a new one."));
        if (otp.attempts >= cfg.otpMaxAttempts()) throw ApiException.badRequest("Too many wrong attempts. Please request a new code.");
        otp.attempts++;
        if (code == null || !MessageDigest.isEqual(otp.codeHash.getBytes(), hash(identity.value(), code.trim()).getBytes())) {
            throw ApiException.badRequest("Incorrect code");
        }
        otp.consumed = true;
    }

    private static String hash(String identity, String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest((identity + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
