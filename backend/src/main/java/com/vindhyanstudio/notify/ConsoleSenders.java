/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Prints messages to the log instead of sending them. Default for local. */
@Configuration
public class ConsoleSenders {

    private static final Logger log = LoggerFactory.getLogger("NOTIFY");

    @Bean
    @ConditionalOnProperty(name = "app.notifications.email", havingValue = "console", matchIfMissing = true)
    MessageSender consoleEmailSender() {
        return sender(Message.Channel.EMAIL);
    }

    @Bean
    @ConditionalOnProperty(name = "app.notifications.sms", havingValue = "console", matchIfMissing = true)
    MessageSender consoleSmsSender() {
        return sender(Message.Channel.SMS);
    }

    private static MessageSender sender(Message.Channel channel) {
        return new MessageSender() {
            @Override
            public Message.Channel channel() {
                return channel;
            }

            @Override
            public void send(Message m) {
                log.info("\n──── {} to {} ────\nSubject: {}\n{}\n────────────────", channel, m.to(), m.subject(), m.body());
            }
        };
    }
}
