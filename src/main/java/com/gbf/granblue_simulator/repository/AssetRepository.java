package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.asset.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByMoveIdIn(List<Long> moveId);

    List<Asset> findAllByActorId(Long actorId);

    @Query("SELECT a FROM Asset a WHERE a.rootCjsName IN (SELECT a1.cjsName FROM Asset a1 WHERE a1.id IN :assetIds)")
    List<Asset> findWithChildrenByAssetId(List<Long> assetIds);

    Asset findByMoveId(Long moveId);
}
