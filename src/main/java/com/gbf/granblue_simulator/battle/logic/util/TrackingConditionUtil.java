package com.gbf.granblue_simulator.battle.logic.util;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class TrackingConditionUtil {

    /**
     * 값을 int 로 반환
     *
     * @return 없으면 0,
     */
    public static int getInt(Map<TrackingCondition, Object> conditionTracker, TrackingCondition key) {
        Object val = conditionTracker.get(key);
        log.info("key = {}, getInt val = {}, val.getClassname = {}", key, val, val.getClass().getName());
        if (val == null) return 0;
        if (val instanceof Number) {
            return ((Number) val).intValue(); // 소숫점 이하 버림
        }
        throw new IllegalArgumentException("[getInt] Invalid conditionTracker value type: " + val.getClass());
    }

    /**
     * 값을 int 로 반환
     *
     * @return 없으면 0,
     */
    public static long getLong(Map<TrackingCondition, Object> conditionTracker, TrackingCondition key) {
        Object val = conditionTracker.get(key);
        if (val == null) return 0;
        if (val instanceof Number) {
            return ((Number) val).longValue(); // 소숫점 이하 버림
        }
        throw new IllegalArgumentException("[getLong] Invalid conditionTracker value type: " + val.getClass());
    }

    /**
     * 값을 String 로 반환
     *
     * @return 없으면 ""
     */
    public static String getString(Map<TrackingCondition, Object> conditionTracker, TrackingCondition key) {
        Object val = conditionTracker.get(key);
        if (val == null) return "";
        if (val instanceof String) {
            return (String) val;
        }
        throw new IllegalArgumentException("[getString] Invalid conditionTracker value type: " + val.getClass());
    }

    /**
     * 누적제가 아닌 컨디션 모두 초기화 (턴 종료시 호출)
     */
    public static void resetAllConditionsNotAcc(Map<TrackingCondition, Object> conditionTracker) {
        if (conditionTracker == null) return;
        conditionTracker.forEach((key, value) -> {
            if (!key.isAccumulative())
                resetCondition(conditionTracker, key);
        });
    }

    public static void resetAllConditions(Map<TrackingCondition, Object> conditionTracker) {
        if (conditionTracker == null) return;
        conditionTracker.forEach((key, value) -> resetCondition(conditionTracker, key));
    }

    public static void resetCondition(Map<TrackingCondition, Object> conditionTracker, TrackingCondition key) {
        Object currentValue = conditionTracker.get(key);
        if (currentValue == null) {
            log.warn("[resetCondition] Key not found: {}", key);
            return;
        }

        Object resetValue = switch (currentValue) {
            case Number n -> 0;
            case Boolean b -> false;
            case String s -> "";
            default -> {
                log.warn("[resetCondition] Unexpected currentValue type, key = {}, currentValue type = {}", key, currentValue.getClass());
                yield null;
            }
        };

        if (resetValue != null) {
            conditionTracker.put(key, resetValue);
        }
    }

}
