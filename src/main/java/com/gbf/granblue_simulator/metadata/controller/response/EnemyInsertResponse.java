package com.gbf.granblue_simulator.metadata.controller.response;

import lombok.Data;

@Data
public class EnemyInsertResponse {

    private final Long enemyId;
    private final String message;

    public static EnemyInsertResponse ok(Long enemyId) {
        return new EnemyInsertResponse(enemyId, "success");
    }
}
