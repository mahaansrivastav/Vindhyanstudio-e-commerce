/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.notify;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {}
