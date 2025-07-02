package com.gbf.granblue_simulator.controller.response.battle;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StartBattleResponse {

    private List<List<String>> effectVideoSrcs;
    private List<List<String>> motionVideoSrcs;

    private List<List<String>> seAudioSrcs;
    private List<List<String>> voiceAudioSrcs;
}
