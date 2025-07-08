package com.gbf.granblue_simulator.controller.request.insert.character;

import com.gbf.granblue_simulator.domain.ElementType;
import lombok.Data;

import java.util.List;

@Data
public class SummonInsertRequest {

    private Long characterId;

    private String name;
    private String nameEn;
    private String info;
    private Double damageRate;
    private Integer hitCount;
    private Integer coolDown;
    private ElementType elementType;

    private List<SummonStatus> statuses;

    @Data
    public static class SummonStatus {
        private String type;
        private String name;
        private String targetType;
        private Integer maxLevel;
        private String effectText;
        private String statusText;
        private Integer duration;
        private String removable;
        private String isResistible;

        private String statusEffects; // type, value \n
    }
}
