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
        MimeMessageHelper h = new MimeMessageHelper(mime, "UTF-8");
        h.setFrom(props.notifications().fromEmail());
        h.setTo(m.to());
        h.setSubject(m.subject());
        h.setText(m.body(), false);
        mail.send(mime);
    }
}
