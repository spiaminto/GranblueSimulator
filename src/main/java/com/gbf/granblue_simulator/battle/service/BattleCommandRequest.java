package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BattleCommandRequest {

    private Member member;
    private Long memberId;
    private Long commandMoveId;

    private Long mainActorId;
    private Long requestActorId;

    private Long summonId;
    private boolean isUnionSummon;

    private StatusEffectTargetType targetType;

    public static BattleCommandRequest of(Long memberId) {
        return BattleCommandRequest.builder()
                .memberId(memberId)
                .mainActorId(null)
                .build();
    }

    public static BattleCommandRequest from(Member member) {
        return BattleCommandRequest.builder()
                .member(member)
                .memberId(member.getId())
                .mainActorId(null)
                .build();
    }

    public static BattleCommandRequest of(Long memberId, Long mainActorId) {
        return BattleCommandRequest.builder()
                .memberId(memberId)
                .mainActorId(mainActorId)
                .build();
    }




}
