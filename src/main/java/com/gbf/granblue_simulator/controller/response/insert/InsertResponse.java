package com.gbf.granblue_simulator.controller.response.insert;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InsertResponse {

    private Long characterId;
    private String message;

    public static InsertResponse ok(Long characterId) {
        return new InsertResponse(characterId, "success");
    }

}
