package com.gbf.granblue_simulator.controller;

import lombok.Data;

@Data
public class SummonRequest {
    private long characterId;
    private long summonId;
    private String moveType;
    private long memberId;
    private long roomId;
}
