package com.gbf.granblue_simulator.metadata.repository;

import com.gbf.granblue_simulator.metadata.domain.visual.EffectVisual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MoveVisualRepository extends JpaRepository<EffectVisual, Long> {

//    List<EffectVisual> findAllByMoveIdIn(List<Long> moveId);

//    List<MoveVisual> findAllByActorId(Long actorId);

//    @Query("SELECT a FROM MoveVisual a WHERE a.rootCjsName IN (SELECT a1.cjsName FROM MoveVisual a1 WHERE a1.id IN :assetIds)")
//    List<MoveVisual> findWithChildrenByAssetId(List<Long> assetIds);

//    List<EffectVisual> findByMoveId(Long moveId);


//    @Query("SELECT a FROM MoveVisual a WHERE a.actorId = :actorId AND a.type = 'ACTOR'")
//    Optional<MoveVisual> findRootAssetByActorId(Long actorId);
}
