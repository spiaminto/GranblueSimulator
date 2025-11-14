package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.repository.actor.BaseActorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ActorService {

    private final BaseActorRepository baseActorRepository;

    public Optional<BaseActor> findById(Long actorId) {
        return baseActorRepository.findById(actorId);
    }

    public List<BaseActor> findAllByIdsOrdered(List<Long> actorIds) {
        Map<Long, BaseActor> actorMap = baseActorRepository.findAllById(actorIds)
                .stream()
                .collect(Collectors.toMap(BaseActor::getId, actor -> actor));
        return actorIds.stream()
                .map(actorMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
