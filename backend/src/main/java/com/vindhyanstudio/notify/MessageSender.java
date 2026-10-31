/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

/**
 * One delivery channel. Implementations are picked by app.notifications.email / app.notifications.sms.
 * Add e.g. Msg91SmsSender or WhatsAppSender by implementing this and registering it
 * under a new property value.
 */
public interface MessageSender {

    Message.Channel channel();

    void send(Message message) throws Exception;
}
