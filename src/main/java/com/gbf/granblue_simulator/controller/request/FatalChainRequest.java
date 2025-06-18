package com.gbf.granblue_simulator.controller.request;

import lombok.Data;

@Data
public class FatalChainRequest {
    private Long memberId;
    private Long characterId;
    private Long moveId;
}
