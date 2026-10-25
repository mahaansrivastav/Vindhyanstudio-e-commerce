package com.vindhyanstudio.storage;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.vindhyanstudio.config.AppProperties;

/**
 * Supabase Storage (free tier: 1 GB). Create two buckets in the Supabase dashboard:
 * a PUBLIC one for product images and a PRIVATE one for invoices and customer photos.
 * Keys starting with "public/" go to the public bucket, everything else to the private one.
 * Env: STORAGE_PROVIDER=supabase, SUPABASE_URL, SUPABASE_SERVICE_KEY (service_role key, server only),
 * SUPABASE_PUBLIC_BUCKET, SUPABASE_PRIVATE_BUCKET.
 */
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseStorageService implements StorageService {

    private final RestClient http;
    private final String baseUrl;
    private final String publicBucket;
    private final String privateBucket;

    public SupabaseStorageService(AppProperties props) {
        AppProperties.Storage.Supabase cfg = props.storage().supabase();
        if (cfg == null || blank(cfg.url()) || blank(cfg.serviceKey())) {
            throw new IllegalStateException("SUPABASE_URL and SUPABASE_SERVICE_KEY must be set when STORAGE_PROVIDER=supabase");
        }
        this.baseUrl = cfg.url().replaceAll("/$", "");
        this.publicBucket = cfg.publicBucket();
        this.privateBucket = cfg.privateBucket();
        this.http = RestClient.builder()
                .baseUrl(baseUrl + "/storage/v1")
                .defaultHeader("Authorization", "Bearer " + cfg.serviceKey())
                .defaultHeader("apikey", cfg.serviceKey())
                .build();
    }

    private record Loc(String bucket, String path) {}

    private Loc loc(String key) {
        if (key.contains("..")) throw new IllegalArgumentException("Invalid storage key");
        return key.startsWith("public/") ? new Loc(publicBucket, key.substring("public/".length())) : new Loc(privateBucket, key);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Loc l = loc(key);
        http.post().uri("/object/{bucket}/{path}", l.bucket(), l.path())
                .header("x-upsert", "true")
                .contentType(MediaType.parseMediaType(contentType == null ? "application/octet-stream" : contentType))
                .body(content)
                .retrieve().toBodilessEntity();
    }

    @Override
    public byte[] get(String key) {
        Loc l = loc(key);
        return http.get().uri("/object/{bucket}/{path}", l.bucket(), l.path()).retrieve().body(byte[].class);
    }

    @Override
    public boolean exists(String key) {
        Loc l = loc(key);
        try {
            http.head().uri("/object/{bucket}/{path}", l.bucket(), l.path()).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException e) {
            return false;
        }
    }

    @Override
    public void delete(String key) {
        Loc l = loc(key);
        http.method(org.springframework.http.HttpMethod.DELETE).uri("/object/{bucket}", l.bucket())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefixes", List.of(l.path())))
                .retrieve().toBodilessEntity();
    }

    @Override
    public String publicUrl(String key) {
        Loc l = loc(key);
        return baseUrl + "/storage/v1/object/public/" + l.bucket() + "/" + l.path();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
