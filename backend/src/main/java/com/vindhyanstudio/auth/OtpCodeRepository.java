package com.vindhyanstudio.auth;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findFirstByIdentityOrderByCreatedAtDesc(String identity);

    long countByIdentityAndCreatedAtAfter(String identity, OffsetDateTime after);
}
