package com.gbf.granblue_simulator.repository.move;

import com.gbf.granblue_simulator.domain.move.prop.asset.LegacyAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyAssetRepository extends JpaRepository<LegacyAsset, Long> {
}
