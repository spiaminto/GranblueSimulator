package com.gbf.granblue_simulator.logic;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.Move;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder(builderMethodName = "")
@Getter
public class SetStatusRequest {
    private BattleActor mainActor;
    private BattleActor enemy;
    private List<BattleActor> partyMembers;
    private Move move;

    public static SetStatusRequestBuilder builder(BattleActor mainActor, Move move) {
        return new SetStatusRequestBuilder()
                .mainActor(mainActor)
                .move(move);
    }
}
