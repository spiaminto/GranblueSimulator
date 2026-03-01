package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByIsHiddenIs(boolean hidden);

    // Repository
    @Modifying
    @Query("UPDATE Room r SET r.unionSummonId = :unionSummonId WHERE r.id = :roomId")
    void updateUnionSummonIdById(@Param("roomId") Long roomId, @Param("unionSummonId") Long unionSummonId);

    List<Room> findByOwnerIdAndRoomStatus(Long ownerId, RoomStatus roomStatus);

    List<Room> findAllByRoomStatus(RoomStatus roomStatus);
}
