package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActorRepository extends JpaRepository<Actor, Long> {

    /**
     * 지정된 Actor 에 대한 락 획득
     *
     * @param actorIds
     * @return
     */
    @Query(value = """
            SELECT a.id
            FROM {h-schema} actor a
            WHERE a.id IN (:actorIds)
            ORDER BY a.id
            FOR UPDATE
            """, nativeQuery = true)
    List<Long> lockActors(@Param("actorIds") List<Long> actorIds);

    /**
     * roomId 에 딸린 모든 Member.Actors 의 id 조회. Actor.id asc 로 순서 보장필요
     * @param roomId
     * @return
     */
    @Query("SELECT a.id FROM Actor a WHERE a.member.room.id = :roomId ORDER BY a.id")
    List<Long> findActorIdsByRoomId(@Param("roomId") Long roomId);

}

//CHECK 나중에 Actor fetchjoin 할때 카테시안 곱 문제 생각할것

