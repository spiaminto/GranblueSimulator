package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StatusEffectRepository extends JpaRepository<StatusEffect, Long> {

    // BattleContext 에 의해 Actor Lock 이전에 먼저 조회되므로, 삭제시 직접 삭제
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query("DELETE FROM StatusEffect se WHERE se.id = :id")
//    void deleteById(@Param("id") Long id);
//
//    @Modifying(clearAutomatically = true, flushAutomatically = true)
//    @Query("DELETE FROM StatusEffect se WHERE se.id IN :ids")
//    void deleteAllById(@Param("ids") List<Long> ids);
}
