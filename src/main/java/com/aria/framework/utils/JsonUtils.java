package com.aria.framework.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Utility class wrapping Jackson's ObjectMapper for serialization and deserialization.
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
        // Prevent instantiation
    }

    /**
     * Convert a Java object to its JSON string representation.
     *
     * @param object any Java object
     * @return String JSON content
     */
    public static String serialize(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            String typeName = object == null ? "null" : object.getClass().getName();
            throw new IllegalArgumentException("Failed to serialize object of type " + typeName, e);
        }
    }

    /**
     * Deserialize a JSON string to a specific Java class target.
     *
     * @param json JSON content
     * @param clazz Class target
     * @param <T> Target Type
     * @return T deserialized class instance
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON to " + clazz.getSimpleName(), e);
        }
    }
}
