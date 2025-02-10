package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.BattleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BattleLogRepository extends JpaRepository<BattleLog, Long> {
}
