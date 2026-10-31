/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.vindhyanstudio.config.AppProperties;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends email through any SMTP server (Brevo, Gmail, SES ...).
 * Configure with SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD
 * and NOTIFY_EMAIL=smtp.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.email", havingValue = "smtp")
public class SmtpEmailSender implements MessageSender {

    private final JavaMailSender mail;
    private final AppProperties props;

    public SmtpEmailSender(JavaMailSender mail, AppProperties props) {
        this.mail = mail;
        this.props = props;
    }

    @Override
    public Message.Channel channel() {
        return Message.Channel.EMAIL;
    }

    @Override
    public void send(Message m) throws Exception {
        MimeMessage mime = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(mime, m.html() != null, "UTF-8");
        h.setFrom(props.notifications().fromEmail());
        // Customers who hit "Reply" reach the shop's real inbox, not the sending address.
        String replyTo = props.seller().email();
        if (replyTo != null && replyTo.contains("@") && !replyTo.endsWith(".local")) h.setReplyTo(replyTo);
        h.setTo(m.to());
        h.setSubject(m.subject());
        if (m.html() != null) h.setText(m.body(), m.html());
        else h.setText(m.body(), false);
        mail.send(mime);
    }
}
