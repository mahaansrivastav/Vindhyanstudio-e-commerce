package com.vindhyanstudio.storage;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vindhyanstudio.common.ApiException;
import com.vindhyanstudio.common.CurrentUser;

/** Customer photo uploads for customised products (lithophanes, 3D models, ...). Stored privately. */
@RestController
public class UploadController {

    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png");

    private final StorageService storage;

    public UploadController(StorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/api/uploads")
    Map<String, String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) throw ApiException.badRequest("Empty file");
        byte[] bytes = file.getBytes();
        String type = sniff(bytes);
        if (type == null || !ALLOWED.contains(type)) throw ApiException.badRequest("Only JPG or PNG photos are allowed");
        String ext = type.equals("image/png") ? ".png" : ".jpg";
        String key = "private/uploads/" + CurrentUser.id() + "/" + UUID.randomUUID() + ext;
        storage.put(key, bytes, type);
        return Map.of("key", key);
    }

    /** Checks magic bytes rather than trusting the browser-supplied content type. */
    private static String sniff(byte[] b) {
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "image/jpeg";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        return null;
    }
}
