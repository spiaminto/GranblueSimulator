package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByIsHiddenIs(boolean hidden);

}
