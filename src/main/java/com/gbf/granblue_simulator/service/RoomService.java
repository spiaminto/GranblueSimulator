package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.repository.RoomRepository;
import com.gbf.granblue_simulator.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    public Room findById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }
    
    public Room addRoom(Room room, Long ownerId) {
        User user = userRepository.findById(ownerId).orElseThrow(() -> new IllegalArgumentException("owner 가 조회되지 않음"));
        room.setOwner(user.getId(), user.getUsername());
        room.setEnemyActorId();
        return save(room, ownerId);
    }

    private Room save(Room room, Long ownerId) {
        return roomRepository.save(room);
    }

}
