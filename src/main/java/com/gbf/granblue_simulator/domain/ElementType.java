package com.gbf.granblue_simulator.domain;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum ElementType {
    FIRE, // 화
    WATER, // 수
    WIND, // 풍
    EARTH, // 토
    LIGHT, // 빛
    DARK, // 암

    // 나중에 천원 구현할때 사용예정
//    FIRE_WIND, // 화풍
//    WATER_EARTH, // 수토
//    LIGHT_DARK, // 광암
    
    NONE, // 무속성
    RANDOM // 랜덤속성 -> 로직에서 변환해서사용
    ;

    // 유리속성 확인
    public boolean isAdvantageTo(ElementType target) {
        return switch (this) {
            case FIRE -> target == WIND || target == NONE;
            case WATER -> target == FIRE || target == NONE;
            case WIND -> target == EARTH || target == NONE;
            case EARTH -> target == WATER || target == NONE;
            case LIGHT -> target == DARK || target == NONE;
            case DARK -> target == LIGHT || target == NONE;
            default -> false;// 무속성 공격의 고정데미지 이므로 유불리 적용 x
        };
    }

    // 불리속성 확인 (무속성은 무상성)
    public boolean isDisadvantageTo(ElementType target) {
        return switch (this) {
            case FIRE -> target == WATER;
            case WATER -> target == EARTH;
            case WIND -> target == FIRE;
            case EARTH -> target == WIND;
            // LIGHT, DARK 는 불리속성 없음
            default -> false;
        };
    }

    // 랜덤 속성 반환
    public static ElementType getRandomElementType() {
        ElementType[] mainElements = {FIRE, WATER, WIND, EARTH, LIGHT, DARK};
        int idx = ThreadLocalRandom.current().nextInt(mainElements.length);
        return mainElements[idx];
    }

}
