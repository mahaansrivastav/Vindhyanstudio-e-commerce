/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

/** One outgoing message. {@code html} is optional: email senders use it when present, with {@code body} as the plain-text version. */
public record Message(Channel channel, String to, String subject, String body, String html, Long orderId) {

    public Message(Channel channel, String to, String subject, String body, Long orderId) {
        this(channel, to, subject, body, null, orderId);
    }

    public enum Channel { EMAIL, SMS }
}
