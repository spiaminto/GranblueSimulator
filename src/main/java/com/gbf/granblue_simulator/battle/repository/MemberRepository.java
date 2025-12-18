package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByRoomIdAndUserId(Long roomId, Long userId);

    Long user(User user);
}
