package com.gbf.granblue_simulator.party.repository;

import com.gbf.granblue_simulator.party.domain.Party;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PartyRepository extends JpaRepository<Party, Long> {


}
