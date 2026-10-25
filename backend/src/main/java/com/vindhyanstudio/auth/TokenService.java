package com.vindhyanstudio.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.customer.Customer;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final AppProperties props;

    public TokenService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public boolean isAdmin(Customer c) {
        List<String> admins = props.admin().identityList();
        return (c.getEmail() != null && admins.contains(c.getEmail().toLowerCase()))
                || (c.getPhone() != null && admins.contains(c.getPhone()));
    }

    public String issue(Customer c) {
        List<String> roles = new ArrayList<>(List.of("CUSTOMER"));
        if (isAdmin(c)) roles.add("ADMIN");
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("vindhyanstudio")
                .subject(String.valueOf(c.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.auth().tokenTtlHours(), ChronoUnit.HOURS))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
