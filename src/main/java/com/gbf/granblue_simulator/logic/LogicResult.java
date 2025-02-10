package com.gbf.granblue_simulator.logic;

public class LogicResult {
    private Integer[] damages; // 데미지
    private Integer[][] additionalDamages; // 추격 데미지 [[1-1타, 1-2타][2-1타, 2-2타][]]

    private Integer hitCount; // 데미지 발생 갯수
    private Integer additionalHitCount; // 추격 발생갯수

    private Integer effectCount; // 모션 재생 갯수
}
