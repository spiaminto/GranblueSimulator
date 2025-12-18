package com.gbf.granblue_simulator.party.controller.dto;

import lombok.Data;

@Data
public class UserEditRequest {

    private Long userId;
    private Long primaryPartyId;

}
