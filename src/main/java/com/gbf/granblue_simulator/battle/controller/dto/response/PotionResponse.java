package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.battle.controller.dto.info.AssetInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.CharacterBattleInfo;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data @Builder
public class PotionResponse {

    @Builder.Default
    private List<Integer> heals = new ArrayList<>();
    @Builder.Default
    private List<Integer> hps = new ArrayList<>();
    @Builder.Default
    private List<Integer> hpRates = new ArrayList<>();

    private Integer potionCount;
    private Integer allPotionCount;
    private Integer elixirCount;

    private Integer actorIndex;

    // 죽엇다 살아난 캐릭터 정보
    private CharacterBattleInfo characterInfo;
    private AssetInfo assetInfo;


}
