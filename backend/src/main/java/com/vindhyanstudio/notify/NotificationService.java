package com.vindhyanstudio.notify;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Sends a message on the matching channel, never throws, and records the outcome. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final List<MessageSender> senders;
    private final NotificationLogRepository logRepo;

    public NotificationService(List<MessageSender> senders, NotificationLogRepository logRepo) {
        this.senders = senders;
        this.logRepo = logRepo;
    }

    public void email(String to, String subject, String body, Long orderId) {
        if (to != null && !to.isBlank()) send(new Message(Message.Channel.EMAIL, to, subject, body, orderId));
    }

    public void sms(String to, String body, Long orderId) {
        if (to != null && !to.isBlank()) send(new Message(Message.Channel.SMS, to, null, body, orderId));
    }

    public void send(Message m) {
        MessageSender sender = senders.stream().filter(s -> s.channel() == m.channel()).findFirst().orElse(null);
        if (sender == null) {
            log.warn("No sender configured for {}", m.channel());
            return;
        }
        try {
            sender.send(m);
            logRepo.save(new NotificationLog(m, "SENT", null));
        } catch (Exception e) {
            log.error("Failed to send {} to {}", m.channel(), m.to(), e);
            logRepo.save(new NotificationLog(m, "FAILED", e.getMessage()));
        }
    }
}
