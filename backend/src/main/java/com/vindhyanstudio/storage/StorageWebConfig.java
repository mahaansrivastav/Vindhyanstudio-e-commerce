package com.vindhyanstudio.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the local storage's public folder. Not needed once files live on a CDN/bucket. */
@Configuration
@ConditionalOnBean(LocalStorageService.class)
public class StorageWebConfig implements WebMvcConfigurer {

    private final LocalStorageService storage;

    public StorageWebConfig(LocalStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/public/**")
                .addResourceLocations(storage.root().resolve("public").toUri().toString() + "/");
    }
}
