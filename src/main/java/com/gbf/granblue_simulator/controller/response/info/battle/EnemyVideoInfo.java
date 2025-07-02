package com.gbf.granblue_simulator.controller.response.info.battle;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class EnemyVideoInfo {
    private List<String> classNames;
    private Integer effectHitDelay;
}
