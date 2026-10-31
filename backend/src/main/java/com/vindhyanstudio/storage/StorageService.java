/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.storage;

import java.io.InputStream;

/**
 * File storage abstraction. Keys look like "public/products/KR-001/1.jpg" or
 * "private/uploads/42/abc.jpg". Keys under "public/" are served to anyone;
 * everything else must be streamed through an authorised endpoint.
 *
 * Implementations: {@link LocalStorageService} (local disk). Add a
 * SupabaseStorageService / S3StorageService and select it with app.storage.provider.
 */
public interface StorageService {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    boolean exists(String key);

    void delete(String key);

    /** Browser-usable URL for a public key. */
    String publicUrl(String key);

    default void put(String key, InputStream in, String contentType) {
        try (in) {
            put(key, in.readAllBytes(), contentType);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
