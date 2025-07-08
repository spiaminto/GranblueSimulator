package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByRoomIdAndUserId(Long roomId, Long userId);

    Long user(User user);
}
