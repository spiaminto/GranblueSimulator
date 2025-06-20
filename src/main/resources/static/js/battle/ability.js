function processAbility(responseAbilityData) {
    let abilityData = responseAbilityData;
    let charOrder = responseAbilityData.charOrder;
    let moveType = MoveType.byName(responseAbilityData.moveType);
    let abilityOrder = moveType === MoveType.FIRST_ABILITY ? 1 : moveType === MoveType.SECOND_ABILITY ? 2 : MoveType.THIRD_ABILITY ? 3 : -1;
    let abilityHitCount = abilityData.totalHitCount;
    let abilityDamages = abilityData.damages;
    let elementType = abilityData.elementTypes[0];
    let chargeGauges = abilityData.chargeGauges;
    let hps = abilityData.hps;
    let hpRates = abilityData.hpRates;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = abilityData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = abilityData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = abilityData.battleStatusList;

    // 준비 - 아군
    let partySelector = '.party-' + charOrder;
    let abilityMotionVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.motion');
    let abilityEffectVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.effect');
    let idleMotionVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);
    let abilityEffectDuration = abilityEffectVideo.length > 0 ? abilityEffectVideo.get(0).duration * 1000 - 100 : 0; // 어빌리티 이펙트 재생 길이, seconds to milliseconds, 영상 딜레이 100ms 보
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

// EFFECT 시작
    // 오디오 재생
    let audioPlayer = new AudioPlayer();
    let audioSrcs = $('.party-audio-container ' + partySelector + ' .' + moveType.className).toArray().map(audio => $(audio).attr('src'));
    audioPlayer.loadSounds(audioSrcs).then(() => audioPlayer.playAllSounds());

    // 아군 이펙트, 모션 재생
    if (abilityEffectVideo.length) {
        playVideo(abilityEffectVideo, abilityMotionVideo, idleMotionVideo);
    }

    // 데미지 삽입
    let $abilityDamageWrapper = $('.ability-damage-wrapper');
    abilityDamages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(charOrder, elementType, 'ability', damageIndex, damage, []);
        $abilityDamageWrapper.prepend($damageElements.$damage);
    })
    // 데미지 마다 표시, 적 피격 모션 재생
    let damageShowClass = abilityDamages.length > 2 ? 'multiple-damage-show' : 'damage-show'
    abilityDamages.forEach(function (damage, damageIndex, damageArray) {
        let startDelay = abilityEffectDuration / damageArray.length * damageIndex;
        if (damageIndex === 0) {
            $enemyIdleVideo.addClass('hidden').get(0).pause();
            $enemyIdleVideo.get(0).currentTime = 0;
            $enemyDamagedVideo.removeClass('hidden');
        }
        setTimeout(function () {
            // 피격 비디오 연속재생의 경우 idle 과 교차재생하면 부자연스러워 지므로 연속재생후 마지막에 되돌림
            $enemyDamagedVideo.get(0).currentTime = 0; // 빼면 부자연스러워짐
            $enemyDamagedVideo.get(0).play();
            // 데미지 표시
            $('.ability-damage-wrapper .damage-index-' + damageIndex).addClass(damageShowClass);
            if (damageIndex >= damageArray.length - 1) { // 마지막
                // 적 모션 정상화
                setTimeout(function () {
                    $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
                    $enemyDamagedVideo.addClass('hidden');
                }, 200) // 막타에 대한 대기 (임의길이)
                // 마지막 데미지 페이드 아웃시 전체 제거
                setTimeout(function () {
                    $abilityDamageWrapper.empty()
                }, 1000); // 바지막 데미지 페이드 아웃 대기
            }
        }, startDelay);
    });

    // 스테이터스 아이콘 갱신
    processStatusIconSync(currentBattleStatusesList, abilityEffectDuration);

    // 어빌리티는 타수가 많을경우 데미지 표시길이만큼 딜레이 보정
    abilityEffectDuration = abilityEffectDuration += abilityHitCount * 50;

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, abilityEffectDuration);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(abilityEffectDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processAbility] totalTime', totalEndTime, 'abilityDuration ', abilityEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}


function processEnemyAbility(responseAbilityData) {
    // 변수초기화
    let abilityData = responseAbilityData;
    let charOrder = abilityData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(abilityData.moveType);
    let abilityHitCount = abilityData.totalHitCount; // 히트수 (피격모션, 데미지 표시관련)
    let damages = abilityData.damages;
    let additionalDamages = abilityData.additionalDamages;
    let elementTypes = abilityData.elementTypes;
    let chargeGauges = abilityData.chargeGauges;
    let hps = abilityData.hps;
    let hpRates = abilityData.hpRates;
    // 적 한정
    let targetOrders = abilityData.enemyAttackTargetOrders;
    let isAllTarget = abilityData.allTarget; // 전체공격여부
    let isEnemyPowerUp = abilityData.enemyPowerUp;
    let isEnemyCtMax = abilityData.enemyCtMax;
    let uniqueTargetOrders = [...new Set(targetOrders)];

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = abilityData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = abilityData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = abilityData.battleStatusList;

    // 준비 - 적
    let $abilityVideo =
        isEnemyPowerUp ? $('.global-video-container .enemy-power-up') :
            isEnemyCtMax ? $('.global-video-container .enemy-ct-max') :
                $('.enemy-video-container .' + moveType.className);
    let effectHitDelay = Number($abilityVideo.attr('data-effect-hit-delay')); // 이펙트 시작 ~ 데미지 표시 까지 딜레이
    let abilityDuration = $abilityVideo.attr('src') ? $abilityVideo.get(0).duration * 1000 : 0;
    abilityDuration = $abilityVideo.hasClass('global-video') ? abilityDuration / 2 : abilityDuration; // 글로벌 이펙트의 경우 길이 반감
    let abilityHitDuration = (abilityDuration - effectHitDelay) / abilityHitCount; // 히트당 길이는 데미지 표시용 1000ms 제외
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    // 준비 - 아군
    let $partyDamagedVideos = $('.party-video-container .' + MoveType.DAMAGED.className); // 문서순서 = order 순서
    let $partyIdleVideos = $('.party-video-container .' + MoveType.IDLE.className);

// EFFECT 이펙트 시작

    // 오디오 재생
    let enemyAudioPlayer = new AudioPlayer();
    let audioSelector =
        isEnemyPowerUp ? '.global-audio-container .enemy-power-up' :
            isEnemyCtMax ? '.global-audio-container .enemy-ct-max ' : '.enemy-audio-container .' + moveType.className;
    let audioSrcs = $(audioSelector).toArray().map(element => $(element).attr('src'));
    enemyAudioPlayer.loadSounds(audioSrcs).then(() => {
        enemyAudioPlayer.playAllSounds();
    });

    // 이펙트 재생
    let $playIdleVideo = isEnemyPowerUp || isEnemyCtMax ? null : $enemyIdleVideo; // 적 파워업 또는 CTMAX 의 경우 이펙트만 재생
    if ($abilityVideo.length && $abilityVideo.attr('src')) {
        playVideo($abilityVideo, null, $playIdleVideo);
    }

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    damages.forEach(function (damage, index) {
        let startDelay = isAllTarget ? 0 : abilityHitDuration * index; // 오의는 전체공격시 1타뿐이므로 0 (+ efectHitDelay)
        let targetOrder = isAllTarget ?
            targetOrders[index % targetOrders.length] :
            targetOrders[index];
        let elementType = elementTypes[index];

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', index, damage, additionalDamages[index], true);
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
            playVideo($partyDamagedVideos.eq(targetOrder - 1), null, $partyIdleVideos.eq(targetOrder - 1));
            // 마지막 공격시 종료후 데미지 삭제.
            if (index >= damages.length - 1) {
                setTimeout(function () {
                    $appendedEnemyDamageWrappers.forEach(function (wrapper) {
                        $(wrapper).remove();
                    })
                }, 1000);
            }
        }, startDelay + effectHitDelay)
    })

    // 스테이터스 아이콘 갱신
    processStatusIconSync(currentBattleStatusesList, abilityDuration);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, abilityDuration);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(abilityDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processEnemyAbility] totalTime', totalEndTime, 'abilityDuration ', abilityDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}

function processFatalChain(responseData) {
    let charOrder = responseData.charOrder;
    let moveType = MoveType.byName(responseData.moveType);
    let moveId = responseData.summonId;
    let totalHitcount = responseData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let damages = responseData.damages;
    let elementType = responseData.elementTypes[0];
    let chargeGauges = responseData.chargeGauges;
    let hps = responseData.hps;
    let hpRates = responseData.hpRates;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = responseData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = responseData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = responseData.battleStatusList;

    // 준비
    let partySelector = '.party-' + charOrder;
    let $effectVideo = $('.global-video-container .fatal-chain-video');
    let partyVideoCount = $('.party-video-container>div').length;
    let $partyEffectVideos = Array.from({length: Number(partyVideoCount)}, (value, index) =>
        $('.party-video-container .party-' + (index + 1) + ' .ability.motion').eq(0)
    ); // 페이탈 체인의 경우 아군 전체의 싱글어택 비디오를 재생
    let $partyIdleVideos = $('.party-video-container .idle');
    let effectStartDelay = 100; // 파티 공격 ~ 페이탈 체인 이펙트 시작까지 딜레이
    let effectDuration = $effectVideo.get(0).duration * 1000 + effectStartDelay;
    let damageHitDelay = effectDuration - 250; // 데미지 히트 딜레이
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

// EFFECT 시작
// 오디오 재생
    let audioSrc = $('.global-audio-container .fatal-chain-audio').attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        setTimeout(() => audioPlayer.playAllSounds(), effectStartDelay);
    });

    // 데미지 채우기 -> 어빌리티 에다가 채움
    damages.forEach(function (damage, index) {
        let $damageElement = getDamageElement(charOrder, elementType, 'ability', index, damage, []);
        $('.ability-damage-wrapper').prepend($damageElement.$damage);
    })

    // 파티 전체 공격 재생
    $partyEffectVideos.forEach(function (partyEffectVideo, index) {
        playVideo($(partyEffectVideo), null, $partyIdleVideos.eq(index));
    })
    // 페이탈 체인 이펙트 재생
    setTimeout(() => playVideo($effectVideo, null, null), effectStartDelay);

    // 피격 이펙트, 데미지 표시
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyDamagedVideo, null, $enemyIdleVideo);
        // 화면 흔들기
        // $('#videoContainer').addClass('push-left-down-effect');
        // setTimeout(function () {
        //     $('#videoContainer').removeClass('push-left-down-effect');
        // }, 150);
        // 데미지 표시
        let damageShowClass = damages.length > 2 ? 'multiple-damage-show' : 'damage-show'
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
    processStatusIconSync(currentBattleStatusesList, effectDuration);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, effectDuration + 500);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processFatalChain] totalTime', totalEndTime, 'effectDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 100));
}