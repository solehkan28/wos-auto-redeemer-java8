package com.wos.redeemer.repository;

import com.wos.redeemer.entity.RedemptionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedemptionLogRepository
        extends JpaRepository<RedemptionLog, Long> {

    List<RedemptionLog> findTop100ByOrderByCreatedAtDesc();
}
