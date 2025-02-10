package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.move.prop.asset.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
}
