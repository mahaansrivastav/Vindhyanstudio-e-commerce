package com.vindhyanstudio.common;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Small static JSON helper for the text columns that hold JSON. */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private Json() {}

    public static String write(Object value) {
        return value == null ? null : MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static <T> T read(String json, TypeReference<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }
}
