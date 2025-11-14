package com.gbf.granblue_simulator.controller.dto.request.insert.enemy;

import com.gbf.granblue_simulator.domain.base.types.ElementType;
import lombok.Data;

@Data
public class EnemyInsertRequest {

    private String name;
    private String nameEn;
    private String backgroundImageSrc;
    private String hpTriggers; // "100, 90, 80 ... "
    private String bgmTriggers; // "100, 90, 80 ... "
    private String bgmSrcs; // \n 으로 구분
    private ElementType elementType;

    // move dead
    private String deadEffectVideoSrc;
    private String deadSeAudioSrc;

    // form-change
    private String formChangeEffectVideoSrc;
    private String formChangeSeAudioSrc;

}
