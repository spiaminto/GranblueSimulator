package com.gbf.granblue_simulator.battle.logic;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerPhase;
import com.gbf.granblue_simulator.metadata.domain.move.TriggerType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 반응 전, 반응할 정보를 담음.
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Reaction {
    private Actor actor;
    private Move move;
    private TriggerType triggerType;  // REACT_SELF -> REACT_ENEMY -> REACT_CHARACTER
    private TriggerPhase phase;

    public String toString() {
        return actor.getName() + ": " + move.getBaseMove().getName() + " / triggerType: " + triggerType.name() + " phase: " + phase.name();
    }

}
