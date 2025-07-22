package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
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

    private final ActorRepository actorRepository;

    public Optional<Actor> findById(Long actorId) {
        return actorRepository.findById(actorId);
    }

    public List<Actor> findAllByIdsOrdered(List<Long> actorIds) {
        Map<Long, Actor> actorMap = actorRepository.findAllById(actorIds)
                .stream()
                .collect(Collectors.toMap(Actor::getId, actor -> actor));
        return actorIds.stream()
                .map(actorMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
