package com.texora.secops.siem.repository;

import com.texora.secops.siem.domain.SiemAlertTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SiemAlertTransitionRepository extends JpaRepository<SiemAlertTransition, UUID> {

    List<SiemAlertTransition> findAllByAlertIdOrderByTransitionedAtAsc(UUID alertId);
}
