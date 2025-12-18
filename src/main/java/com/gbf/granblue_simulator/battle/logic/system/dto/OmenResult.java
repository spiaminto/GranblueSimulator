package com.gbf.granblue_simulator.battle.logic.system.dto;

import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class OmenResult {
    private OmenType type;
    private Integer remainValue;
    private String cancelCond;
    private String name;
    private String info;
    private String motion;

    public static OmenResult from(Enemy enemy) {
        Move standby = enemy.getMove(enemy.getCurrentStandbyType());
        Omen omen = standby.getOmen();
        return new OmenResult(
                omen.getOmenType(),
                enemy.getOmenValue(),
                omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo(),
                omen.getName(),
                omen.getInfo(),
                standby.getMotionType().getMotion()
        );
    }
}
