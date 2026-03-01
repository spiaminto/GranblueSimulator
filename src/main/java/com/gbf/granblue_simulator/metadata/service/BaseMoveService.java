package com.gbf.granblue_simulator.metadata.service;

import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.user.domain.UserCharacter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BaseMoveService {

    private final BaseMoveRepository repository;

    public Optional<BaseMove> findById(Long id) {
        return repository.findById(id);
    }

    public List<BaseMove> findAllByIds(List<Long> ids) {
        return repository.findAllById(ids);
    }

    /**
     * 파라미터로 받은 id 로 찾은 BaseMove 를 맵으로 반환<br>
     * BaseMove.moveType 을 키로, BaseMove.logicId 로 정렬하여 같은 moveType 내에서 순서 유지
     * @param ids
     * @return
     */
    public Map<MoveType, List<BaseMove>> findAllByIdsToMap(List<Long> ids) {
        return repository.findAllById(ids).stream().sorted(Comparator.comparing(BaseMove::getLogicId)).collect(Collectors.groupingBy(BaseMove::getType));
    }

    public List<BaseMove> findByLogicIds(String... logicIds) {
        return repository.findAllByLogicIdIn(List.of(logicIds));
    }

    public BaseMove findByLogicId(String logicId) {
        return repository.findByLogicId(logicId).orElseThrow(() -> new IllegalArgumentException("[findByLogicId] logicId = " + logicId + " not found"));
    }

}
