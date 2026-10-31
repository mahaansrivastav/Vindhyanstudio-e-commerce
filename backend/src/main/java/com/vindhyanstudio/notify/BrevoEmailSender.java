package com.vindhyanstudio.notify;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.vindhyanstudio.config.AppProperties;

/**
 * Sends email through Brevo's HTTPS API (port 443), so it works on hosts that block SMTP ports,
 * such as Render's free plan. Configure with NOTIFY_EMAIL=brevo and BREVO_API_KEY.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.email", havingValue = "brevo")
public class BrevoEmailSender implements MessageSender {

    private static final Pattern NAMED = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+)\\s*>\\s*$");

    private final RestClient http;
    private final Map<String, String> sender;
    private final String replyTo;
    private final boolean configured;

    public BrevoEmailSender(AppProperties props) {
        // A missing key must not stop the site from starting; sends fail (and the startup report warns) instead.
        String key = props.notifications().brevoApiKey() == null ? "" : props.notifications().brevoApiKey();
        this.configured = !key.isBlank();
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .requestFactory(timeouts)
                .defaultHeader("api-key", key.trim())
                .build();
        this.sender = address(props.notifications().fromEmail());
        String shop = props.seller().email();
        this.replyTo = shop != null && shop.contains("@") && !shop.endsWith(".local") ? shop : null;
    }

    @Override
    public Message.Channel channel() {
        return Message.Channel.EMAIL;
    }

    @Override
    public void send(Message m) {
        if (!configured) throw new IllegalStateException("BREVO_API_KEY is not set");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", m.to())));
        if (replyTo != null) body.put("replyTo", Map.of("email", replyTo));
        body.put("subject", m.subject());
        body.put("textContent", m.body());
        http.post().uri("/smtp/email").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toBodilessEntity();
    }

    /** "Vindhyan Studio <noreply@x.in>" → {name, email}; a bare address → {email}. */
    static Map<String, String> address(String from) {
        Matcher m = NAMED.matcher(from == null ? "" : from);
        if (m.matches() && !m.group(1).isEmpty()) return Map.of("name", m.group(1).replace("\"", ""), "email", m.group(2));
        return Map.of("email", m.matches() ? m.group(2) : String.valueOf(from).trim());
    }
}
