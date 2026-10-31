/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.order;

/** Published inside the confirming transaction; handled after commit by {@link FulfilmentListener}. */
public record OrderConfirmedEvent(Long orderId) {}
