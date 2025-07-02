package com.gbf.granblue_simulator.controller.request;

import lombok.Builder;
import lombok.Data;

@Data
public class UserEditRequest {

    private Long userId;
    private Long primaryPartyId;

}
