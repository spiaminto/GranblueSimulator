package com.gbf.granblue_simulator.metadata.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InsertResponse {

    private Long actorId;
    private String message;
    private String rootCjsName;

    public static InsertResponse ok(Long actorId) {
        return new InsertResponse(actorId, "success", null);
    }
    public static InsertResponse ok(String rootCjsName) {
        return new InsertResponse(null, "success", rootCjsName);
    }

}
