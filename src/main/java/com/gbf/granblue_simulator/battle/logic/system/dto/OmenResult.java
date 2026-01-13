package com.gbf.granblue_simulator.battle.logic.system.dto;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class OmenResult {
    private OmenType type;
    private MoveType standbyMoveType;
    private Integer remainValue;
    private OmenCancelCond cancelCond;
    private String name;
    private String info;
    private String motion;

    public static OmenResult from(Enemy enemy) {
        Move standby = enemy.getMove(enemy.getCurrentStandbyType());
        Omen omen = standby.getOmen();
        return new OmenResult(
                omen.getOmenType(),
                standby.getType(),
                enemy.getOmenValue(),
                omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()),
                omen.getName(),
                omen.getInfo(),
                standby.getMotionType().getMotion()
        );
    }
}
