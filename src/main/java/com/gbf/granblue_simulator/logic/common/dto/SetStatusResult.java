package com.gbf.granblue_simulator.logic.common.dto;

import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SetStatusResult {
    @Builder.Default
    private List<BattleStatus> enemyAddedStatuses = new ArrayList<>();
    @Builder.Default
    private List<BattleStatus> enemyRemovedStatuses = new ArrayList<>();
    @Builder.Default
    private List<List<BattleStatus>> partyMemberAddedStatuses = new ArrayList<>(); // order by currentOrder, 내부원소도 빈리스트로 초기화됨
    @Builder.Default
    private List<List<BattleStatus>> partyMemberRemovedStatuses = new ArrayList<>();
    @Builder.Default
    private List<Integer> healValues = new ArrayList<>();

}
