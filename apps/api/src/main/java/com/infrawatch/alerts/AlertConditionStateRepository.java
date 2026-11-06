package com.infrawatch.alerts;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertConditionStateRepository extends JpaRepository<AlertConditionState, AlertConditionState.Key> {
}
