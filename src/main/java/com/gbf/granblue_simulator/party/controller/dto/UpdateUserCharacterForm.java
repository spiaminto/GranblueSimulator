package com.gbf.granblue_simulator.party.controller.dto;

import com.gbf.granblue_simulator.user.domain.UserCharacterMoveStatus;
import lombok.Data;

@Data
public class UpdateUserCharacterForm {

    private Long moveId;
    private Long characterId;
    private UserCharacterMoveStatus targetStatus;
}
