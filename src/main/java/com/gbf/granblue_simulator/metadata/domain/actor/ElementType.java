package com.gbf.granblue_simulator.metadata.domain.actor;

import lombok.Getter;

import java.util.concurrent.ThreadLocalRandom;

@Getter
public enum ElementType {
    FIRE("화속성", 1), // 화
    WATER("수속성", 2), // 수
    EARTH("토속성", 3), // 토
    WIND("풍속성", 4), // 풍
    LIGHT("빛속성", 5), // 빛
    DARK("암속성", 6), // 암

    // 나중에 천원 구현할때 사용예정
//    FIRE_WIND, // 화풍
//    WATER_EARTH, // 수토
//    LIGHT_DARK, // 광암
    
    PLAIN("무속성", 10), // 무속성

    SELECTABLE("선택가능", 20), // 속성 변환이 가능한 주인공 전용
    ACTOR("자속성", 30), // Move.Actor 의 속성을 따라감. 속성변환이 가능한 주인공 어빌리티 전용
    RANDOM("랜덤속성", 40), // 랜덤속성 -> 로직에서 변환해서사용

    NONE("없음", 99) // null

    ,
    ;
    
    // CHECK insert 에서 ordinal 사용중

    private final String presentName;
    private final int order;

    ElementType(String presentName, int order) {
        this.presentName = presentName;
        this.order = order;
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
            case PLAIN -> false; // 무속성은 유리 적용 x, 고정데미지
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

    /**
     * 6속성 인지 확인
     * @return
     */
    public boolean isElementalType() {
        return switch (this) {
            case FIRE, WATER, EARTH, WIND, LIGHT, DARK -> true;
            default -> false;
        };
    }

    /**
     * order 값으로 ElementType 리턴, 6속성만 해당 
     * @param order 1 ~ 6 정수
     * @return 6속성 이외 요청시 NONE 반환
     */
    public static ElementType getByOrder(int order) {
        return switch (order) {
            case 1 -> FIRE;
            case 2 -> WATER;
            case 3 -> EARTH;
            case 4 -> WIND;
            case 5 -> LIGHT;
            case 6 -> DARK;
            default -> NONE; // 오류
        };
    }

    // 랜덤 속성 반환
    public static ElementType getRandomElementType() {
        ElementType[] mainElements = {FIRE, WATER, WIND, EARTH, LIGHT, DARK};
        int idx = ThreadLocalRandom.current().nextInt(mainElements.length);
        return mainElements[idx];
    }

}
