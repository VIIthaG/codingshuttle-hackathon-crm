package com.flowcrm.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Deterministic SHA-256 fingerprint of request payloads for idempotency checks.
 */
@Component
public class RequestFingerprint {

    private final ObjectMapper fingerprintMapper;

    public RequestFingerprint() {
        this.fingerprintMapper = JsonMapper.builder()
                .findAndAddModules()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .build();
    }

    public String sha256Hex(Object requestPayload) {
        try {
            byte[] json = fingerprintMapper.writeValueAsBytes(requestPayload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to fingerprint request payload", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /** Visible for tests — canonical JSON used before hashing. */
    String canonicalJson(Object requestPayload) {
        try {
            return fingerprintMapper.writeValueAsString(requestPayload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize request payload", ex);
        }
    }

    public static String sha256HexOfString(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
