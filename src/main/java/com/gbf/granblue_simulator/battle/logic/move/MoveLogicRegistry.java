package com.gbf.granblue_simulator.battle.logic.move;

import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MoveLogicRegistry {

    private final Map<String, MoveLogic> registry = new HashMap<>();

    /**
     * 로직 request
     * @param key
     * @param logic
     */
    public void register(String key, Function<MoveLogicRequest, MoveLogicResult> logic) {
        if (registry.containsKey(key)) {
            log.warn("[register] Duplicate logic key =  {}", key);
        }
        if (key.length() < 6) {
            throw  new IllegalArgumentException("[register] Invalid logic key length, key = " + key);
        }
        registry.put(key, logic::apply);
    }

    public MoveLogic get(String key) {
        MoveLogic logic = registry.get(key);
        if (logic == null) {
            log.error("[get] Logic not found, key = {}", key);
            throw new IllegalArgumentException("[MoveLogicRegistry.get] No logic registered, key = " + key);
        }
        return logic;
    }

    // 디버깅용
    public Map<String, String> getRegisteredKeys() {
        return registry.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getClass().getSimpleName()
                ));
    }
}
