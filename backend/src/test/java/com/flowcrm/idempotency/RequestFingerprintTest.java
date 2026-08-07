package com.flowcrm.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowcrm.enums.LeadSource;
import com.flowcrm.lead.dto.LeadCreateRequest;
import org.junit.jupiter.api.Test;

class RequestFingerprintTest {

    private final RequestFingerprint fingerprint = new RequestFingerprint();

    @Test
    void samePayload_producesSameHash() {
        LeadCreateRequest a = new LeadCreateRequest(
                "Jane Doe", "jane@acme.com", "555", "Acme", LeadSource.WEB, null, null);
        LeadCreateRequest b = new LeadCreateRequest(
                "Jane Doe", "jane@acme.com", "555", "Acme", LeadSource.WEB, null, null);

        assertThat(fingerprint.sha256Hex(a)).isEqualTo(fingerprint.sha256Hex(b));
        assertThat(fingerprint.sha256Hex(a)).hasSize(64);
    }

    @Test
    void differentPayload_producesDifferentHash() {
        LeadCreateRequest a = new LeadCreateRequest(
                "Jane Doe", "jane@acme.com", "555", "Acme", LeadSource.WEB, null, null);
        LeadCreateRequest b = new LeadCreateRequest(
                "John Doe", "jane@acme.com", "555", "Acme", LeadSource.WEB, null, null);

        assertThat(fingerprint.sha256Hex(a)).isNotEqualTo(fingerprint.sha256Hex(b));
    }
}
