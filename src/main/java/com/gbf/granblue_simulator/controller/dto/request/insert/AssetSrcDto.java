package com.gbf.granblue_simulator.controller.dto.request.insert;

import lombok.*;

@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AssetSrcDto {

    private String effectVideoSrc;
    private String motionVideoSrc;
    private String seAudioSrc;
    private String voiceAudioSrc;

}
