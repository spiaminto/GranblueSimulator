package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * Member 와 연관 Actors 를 직접 조회<br>
     * BattleContext 의 정합성을 맞추기 위해 사용 <br>
     * fetch join으로 이미 로드된 엔티티의 하위 컬렉션 lazy load 시 BatchSize 가 적용되지 않음.<br>
     * (fetch join 미포함 컬렉션인 Actor.moves 등은 BatchSize 정상 적용됨)
     */
    @Query("""
    SELECT m FROM Member m
    JOIN FETCH m.actors a
    LEFT JOIN FETCH a.status
    LEFT JOIN FETCH a.actorVisual
    LEFT JOIN FETCH a.baseActor ba
    LEFT JOIN FETCH ba.defaultVisual
    WHERE m.id = :memberId
    """)
    Optional<Member> findWithActorsById(@Param("memberId") Long memberId);
}
