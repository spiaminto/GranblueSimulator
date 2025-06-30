function processAbility(response) {
    // 준비 - 아군
    let $partyVideo = getVideo(response.charOrder, response.moveType);
    let abilityEffectDuration = $partyVideo.effect ? $partyVideo.effect.get(0).duration * 1000 - 100 : 0; // 어빌리티 이펙트 재생 길이, seconds to milliseconds, 영상 딜레이 100ms 보정
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 시작
    // 오디오 재생
    let audioSrcs = $('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className).toArray().map(audio => $(audio).attr('src'));
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => window.effectAudioPlayer.playAllSounds());

    // 아군 이펙트, 모션 재생
    if ($partyVideo.effect) {
        playVideo($partyVideo.effect, $partyVideo.motion, $partyVideo.idle);
    }

    // 데미지 삽입
    let $abilityDamageWrapper = $('.ability-damage-wrapper');
    response.damages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], 'ability', damageIndex, damage, []);
        $abilityDamageWrapper.prepend($damageElements.$damage);
    })
    // 데미지 마다 표시, 적 피격 모션 재생
    let damageShowClass = response.totalHitCount > 2 ? 'multiple-damage-show' : 'damage-show'
    response.damages.forEach(function (damage, damageIndex, damageArray) {
        let startDelay = abilityEffectDuration / damageArray.length * damageIndex;
        if (damageIndex === 0) {
            $enemyVideo.idle.addClass('hidden').get(0).pause();
            $enemyVideo.idle.get(0).currentTime = 0;
            $enemyVideo.effect.removeClass('hidden');
        }
        setTimeout(function () {
            // 피격 비디오 연속재생의 경우 idle 과 교차재생하면 부자연스러워 지므로 연속재생후 마지막에 되돌림
            $enemyVideo.effect.get(0).currentTime = 0; // 빼면 부자연스러워짐
            $enemyVideo.effect.get(0).play();
            // 데미지 표시
            $('.ability-damage-wrapper .damage-index-' + damageIndex).addClass(damageShowClass);
            if (damageIndex >= damageArray.length - 1) { // 마지막
                // 적 모션 정상화
                setTimeout(function () {
                    $enemyVideo.idle.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
                    $enemyVideo.effect.addClass('hidden');
                }, 200) // 막타에 대한 대기 (임의길이)
                // 마지막 데미지 페이드 아웃시 전체 제거
                setTimeout(function () {
                    $abilityDamageWrapper.empty()
                }, 1000); // 바지막 데미지 페이드 아웃 대기
            }
        }, startDelay);
    });

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, abilityEffectDuration);

    // 어빌리티는 타수가 많을경우 데미지 표시길이만큼 딜레이 보정
    abilityEffectDuration = abilityEffectDuration += response.totalHitCount * 50;
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, abilityEffectDuration);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(abilityEffectDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processAbility] totalTime', totalEndTime, 'abilityDuration ', abilityEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}


function processEnemyAbility(response) {
    let isAllTarget = response.allTarget;
    let isEnemyPowerUp = response.enemyPowerUp;
    let isEnemyCtMax = response.enemyCtMax;
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];

    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let $enemyVideo = getVideo(0, response.moveType, idleMoveType);
    let effectDuration = $enemyVideo.effect ? $enemyVideo.effect.get(0).duration * 1000 : 0;
    let $globalEffectVideo =
        isEnemyPowerUp ? $('.global-video-container .enemy-power-up') :
            isEnemyCtMax ? $('.global-video-container .enemy-ct-max') : null;
    if ($globalEffectVideo) { // 글로벌 이펙트의 경우
        $enemyVideo.effect = $globalEffectVideo.eq(0);
        effectDuration = effectDuration / 2;
    }
    let effectHitDelay = Number($enemyVideo.effect.attr('data-effect-hit-delay')); // 이펙트 시작 ~ 데미지 표시 까지 딜레이
    let effectHitDuration = (effectDuration - effectHitDelay) / response.totalHitCount; // 히트당 길이는 데미지 표시용 1000ms 제외

    // 준비 - 아군 (0번은 사용안함)
    let $partyVideos = [-1, 1, 2, 3, 4]
        .map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));

// EFFECT 이펙트 시작

    // 오디오 재생
    let audioSelector =
        isEnemyPowerUp ? '.global-audio-container .enemy-power-up' :
            isEnemyCtMax ? '.global-audio-container .enemy-ct-max ' : '.enemy-audio-container .' + response.moveType.className;
    let audioSrcs = $(audioSelector).toArray().map(element => $(element).attr('src'));
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => {
        window.effectAudioPlayer.playAllSounds();
    });

    // 이펙트 재생
    if ($enemyVideo.effect) {
        let $playIdleVideo = isEnemyPowerUp || isEnemyCtMax ? null : $enemyVideo.idle; // 적 파워업 또는 CTMAX 의 경우 이펙트만 재생
        playVideo($enemyVideo.effect, null, $playIdleVideo);
    }

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    response.damages.forEach(function (damage, index) {
        let startDelay = isAllTarget ? 0 : effectHitDuration * index; // 오의는 전체공격시 1타뿐이므로 0 (+ efectHitDelay)
        let targetOrder = isAllTarget ?
            response.enemyAttackTargetOrders[index % response.enemyAttackTargetOrders.length] :
            response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $('.enemy-damage-wrapper.actor-' + targetOrder).last(); // 이전 행동 공격데미지와 겹치지 않도록 추가한 래퍼 사용
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);

        // 데미지 표시
        setTimeout(function () {
            // 데미지 및 표시
            $damageElements.$damage.addClass('enemy-damage-show');
            // 추가데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('enemy-damage-show');
                }, index + 100);
            });
            // 아군 피격 재생
            playVideo($partyVideos[targetOrder].effect, null, $partyVideos[targetOrder].idle);
            // 마지막 공격시 종료후 데미지 삭제.
            if (index >= response.damages.length - 1) {
                setTimeout(function () {
                    $appendedEnemyDamageWrappers.forEach(function (wrapper) {
                        $(wrapper).remove();
                    })
                }, 1000);
            }
        }, startDelay + effectHitDelay)
    })

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processEnemyAbility] totalTime', totalEndTime, 'abilityDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}

function processFatalChain(response) {
    // 준비 - 아군
    let $effectVideo = $('.global-video-container .fatal-chain-video');
    let effectStartDelay = 100; // 파티 공격 ~ 페이탈 체인 이펙트 시작까지 딜레이
    let effectDuration = $effectVideo.get(0).duration * 1000 + effectStartDelay;
    let damageHitDelay = effectDuration - 250; // 데미지 히트 딜레이
    let $partyFirstAbilityVideos = [1, 2, 3, 4] // 아군의 어빌리티 모션 전체 재생
        .map(number => getVideo(number, MoveType.FIRST_ABILITY, MoveType.IDLE_DEFAULT));
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 시작
// 오디오 재생
    let audioSrc = $('.global-audio-container .fatal-chain-audio').attr('src');
    window.effectAudioPlayer.loadSound(audioSrc, true).then(() => {
        setTimeout(() => window.effectAudioPlayer.playAllSounds(), effectStartDelay);
    });

    // 데미지 채우기 -> 어빌리티 에다가 채움
    response.damages.forEach(function (damage, index) {
        let $damageElement = getDamageElement(response.charOrder, response.elementTypes[0], 'ability', index, damage, []);
        $('.ability-damage-wrapper').prepend($damageElement.$damage);
    })

    // 파티 전체 모션 재생
    $partyFirstAbilityVideos.forEach(function (partyVideo) {
        playVideo(partyVideo.motion, null, partyVideo.idle);
    })
    // 페이탈 체인 이펙트 재생
    setTimeout(() => playVideo($effectVideo, null, null), effectStartDelay);

    // 피격 이펙트, 데미지 표시
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyVideo.effect, null, $enemyVideo.idle);
        // 화면 흔들기
        // $('#videoContainer').addClass('push-left-down-effect');
        // setTimeout(function () {
        //     $('#videoContainer').removeClass('push-left-down-effect');
        // }, 150);
        // 데미지 표시
        let damageShowClass = response.damages.length > 2 ? 'multiple-damage-show' : 'damage-show'
        let $abilityDamages = $('.ability-damage-wrapper .ability-damage');
        $abilityDamages.each(function (index, abilityDamage) {
            setTimeout(function () {
                $(abilityDamage).addClass(damageShowClass);
            }, index * 100)
            if (index >= $abilityDamages.length - 1) {
                // 마지막에 제거
                setTimeout(function () {
                    $abilityDamages.remove();
                }, 1000);
            }
        })
    }, damageHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, effectDuration + 500);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processFatalChain] totalTime', totalEndTime, 'effectDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 100));
}