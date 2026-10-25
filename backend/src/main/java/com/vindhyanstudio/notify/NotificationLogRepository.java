package com.vindhyanstudio.notify;

import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {}
