package com.texora.secops.vpn.policy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyConditionsCodecTest {

    private final PolicyConditionsCodec codec = new PolicyConditionsCodec();

    @Test
    void defaultsAreMadeExplicitAndSecure() {
        AccessPolicyConditions c = codec.fromMap(new LinkedHashMap<>());
        assertThat(c.getEffect()).isEqualTo("ALLOW");
        assertThat(c.getMfaRequired()).isTrue();
        assertThat(c.getDeviceEnrollmentRequired()).isTrue();
    }

    @Test
    void unknownFieldsAreRejectedSoATypoCannotWeakenAPolicy() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mfaRequried", false);
        assertThatThrownBy(() -> codec.fromMap(m)).isInstanceOf(PolicyConditionsException.class);
    }

    @Test
    void nullInputRejected() {
        assertThatThrownBy(() -> codec.fromMap(null)).isInstanceOf(PolicyConditionsException.class);
    }

    @Test
    void semanticValidation() {
        assertThatThrownBy(() -> codec.fromMap(new LinkedHashMap<>(Map.of("effect", "MAYBE")))).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.fromMap(new LinkedHashMap<>(Map.of("maxSessionMinutes", 0)))).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.fromMap(new LinkedHashMap<>(Map.of("maxSessionMinutes", 5000)))).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.fromMap(new LinkedHashMap<>(Map.of("timeWindow", Map.of("zone", "UTC"))))).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.fromMap(new LinkedHashMap<>(Map.of("timeWindow",
                Map.of("zone", "UTC", "startTime", "09:00", "endTime", "18:00", "daysOfWeek", List.of("FUNDAY"))))))
                .isInstanceOf(PolicyConditionsException.class);
    }

    @Test
    void roundTrip() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("effect", "DENY");
        m.put("maxSessionMinutes", 60);
        String json = codec.toJson(codec.fromMap(m));
        AccessPolicyConditions back = codec.parseStored(json);
        assertThat(back.getEffect()).isEqualTo("DENY");
        assertThat(back.getMaxSessionMinutes()).isEqualTo(60);
        assertThat(codec.toMap(json)).containsEntry("effect", "DENY");
    }

    @Test
    void unparseableStoredJsonSurfacesSoCallersTreatItAsAmbiguous() {
        assertThatThrownBy(() -> codec.parseStored("{not json")).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.parseStored("")).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.parseStored(null)).isInstanceOf(PolicyConditionsException.class);
        assertThatThrownBy(() -> codec.parseStored("{\"surprise\":1}")).isInstanceOf(PolicyConditionsException.class);
        assertThat(codec.toMap("{not json")).containsKey("error");
    }
}
