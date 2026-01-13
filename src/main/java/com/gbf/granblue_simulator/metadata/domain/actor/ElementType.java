package com.gbf.granblue_simulator.metadata.domain.actor;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum ElementType {
    FIRE("화속성"), // 화
    WATER("수속성"), // 수
    EARTH("토속성"), // 토
    WIND("풍속성"), // 풍
    LIGHT("빛속성"), // 빛
    DARK("암속성"), // 암

    // 나중에 천원 구현할때 사용예정
//    FIRE_WIND, // 화풍
//    WATER_EARTH, // 수토
//    LIGHT_DARK, // 광암
    
    SELECTABLE("선택가능"), // 속성 변환이 가능한 주인공 전용
    ACTOR("자속성"), // Move.Actor 의 속성을 따라감. 속성변환이 가능한 주인공 어빌리티 전용

    NONE("무속성"), // 무속성
    RANDOM("랜덤속성") // 랜덤속성 -> 로직에서 변환해서사용
    ;
    
    // CHECK insert 에서 ordinal 사용중

    private final String presentName;

    ElementType(String presentName) {
        this.presentName = presentName;
    }

    // 유리속성 확인
    public boolean isAdvantageTo(ElementType target) {
        return switch (this) {
            case FIRE -> target == WIND;
            case WATER -> target == FIRE;
            case WIND -> target == EARTH;
            case EARTH -> target == WATER;
            case LIGHT -> target == DARK;
            case DARK -> target == LIGHT;
            // 무속성에 대한 속성유리판정은 없음 (1배)
            case NONE -> false; // 무속성은 유리 적용 x, 고정데미지
            default -> false;
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
