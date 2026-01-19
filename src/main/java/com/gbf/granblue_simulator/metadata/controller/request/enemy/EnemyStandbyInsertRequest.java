package com.gbf.granblue_simulator.metadata.controller.request.enemy;
import lombok.Data;

@Data
public class EnemyStandbyInsertRequest {

    private Long enemyId;
    private String type;

    private String standbyEffectVideoSrc;
    private String standbySeAudioSrc;

    private OmenRequest omen;

    @Data
    public static class OmenRequest {

        private String name;
        private String type;
        private String info;

        private String cancelConditions; // {type, presentInfo, initValue \n}

        private String triggerHps;

    }

}
