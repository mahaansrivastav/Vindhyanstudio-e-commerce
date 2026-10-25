package com.vindhyanstudio.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.common.CurrentUser;
import com.vindhyanstudio.config.AppProperties;
import com.vindhyanstudio.customer.Customer;
import com.vindhyanstudio.customer.CustomerRepository;

/**
 * Passwordless login: one endpoint sends a code to a mobile number or email, the other verifies it.
 * Mobile and email resolve to the same account once linked (BRD FR-01, FR-02).
 */
@RestController
public class AuthController {

    private final OtpService otp;
    private final TokenService tokens;
    private final CustomerRepository customers;
    private final AppProperties props;

    public AuthController(OtpService otp, TokenService tokens, CustomerRepository customers, AppProperties props) {
        this.otp = otp;
        this.tokens = tokens;
        this.customers = customers;
        this.props = props;
    }

    public record OtpRequest(String identity) {}

    public record OtpVerify(String identity, String code) {}

    @PostMapping("/api/auth/otp/request")
    Map<String, Object> request(@RequestBody OtpRequest body) {
        Identity id = Identity.parse(body.identity());
        String code = otp.issue(id);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("sentTo", id.value());
        res.put("channel", id.type());
        if (props.auth().exposeOtp()) res.put("devOtp", code);
        return res;
    }

    @PostMapping("/api/auth/otp/verify")
    @Transactional
    Map<String, Object> verify(@RequestBody OtpVerify body) {
        Identity id = Identity.parse(body.identity());
        otp.verify(id, body.code());
        Customer c = find(id).orElseGet(() -> {
            Customer n = new Customer();
            if (id.type() == Identity.Type.EMAIL) n.setEmail(id.value()); else n.setPhone(id.value());
            return customers.save(n);
        });
        return Map.of("token", tokens.issue(c), "admin", tokens.isAdmin(c));
    }

    /** Adds a second login method (mobile or email) to the logged-in account. */
    @PostMapping("/api/me/link/request")
    Map<String, Object> linkRequest(@RequestBody OtpRequest body) {
        Identity id = Identity.parse(body.identity());
        find(id).filter(c -> !c.getId().equals(CurrentUser.id())).ifPresent(c -> {
            throw new ApiException(HttpStatus.CONFLICT, "This " + id.type().name().toLowerCase() + " is already used by another account");
        });
        return request(body);
    }

    @PostMapping("/api/me/link/verify")
    @Transactional
    Map<String, Object> linkVerify(@RequestBody OtpVerify body) {
        Identity id = Identity.parse(body.identity());
        otp.verify(id, body.code());
        Customer c = customers.findById(CurrentUser.id()).orElseThrow();
        find(id).filter(o -> !o.getId().equals(c.getId())).ifPresent(o -> {
            throw new ApiException(HttpStatus.CONFLICT, "Already used by another account");
        });
        if (id.type() == Identity.Type.EMAIL) c.setEmail(id.value()); else c.setPhone(id.value());
        return Map.of("token", tokens.issue(c), "admin", tokens.isAdmin(c));
    }

    private java.util.Optional<Customer> find(Identity id) {
        return id.type() == Identity.Type.EMAIL ? customers.findByEmailIgnoreCase(id.value()) : customers.findByPhone(id.value());
    }
}
