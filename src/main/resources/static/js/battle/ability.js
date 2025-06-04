function processAbility(responseAbilityData) {
    let abilityData = responseAbilityData;
    let charOrder = responseAbilityData.charOrder;
    let moveType = MoveType.byName(responseAbilityData.moveType);
    let abilityOrder = moveType === MoveType.FIRST_ABILITY ? 1 : moveType === MoveType.SECOND_ABILITY ? 2 : MoveType.THIRD_ABILITY ? 3 : -1;
    let abilityHitCount = abilityData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let abilityDamages = abilityData.damages;
    let elementType = abilityData.elementTypes[0];
    let chargeGauges = abilityData.chargeGauges;
    let hps = abilityData.hps;
    let hpRates = abilityData.hpRates;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = abilityData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = abilityData.battleStatusList;
    // 아군(시전자) 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList[charOrder].length > 0 || addedBuffStatusesList[0].length > 0;
    let hasDebuff = addedDebuffStatusesList[charOrder].length > 0 || addedDebuffStatusesList[0].length > 0;

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
    // 준비 - 오디오
    let audioPlayers = new Map();
    audioPlayers.set('char', new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());
    let charAudioPlayer = audioPlayers.get('char');

// EFFECT 시작
    // 오디오 재생
    let audioSrcs = $('.party-audio-container ' + partySelector + ' .' + moveType.className).toArray().map(audio => audio.src);
    charAudioPlayer.loadSounds(audioSrcs).then(() => charAudioPlayer.playAllSounds());

    // 아군 이펙트, 모션 재생
    if (abilityEffectVideo) {
        playVideo(abilityEffectVideo, abilityMotionVideo, idleMotionVideo);
    }

    // 데미지 삽입
    let $abilityDamageWrapper = $('.ability-damage-wrapper');
    abilityDamages.forEach(function (damage, damageIndex) {
        $abilityDamageWrapper.prepend($('<div>', {
            class: 'ability-damage ability-damage-' + damageIndex + ' element-type-' + elementType.toLowerCase(),
            text: damage,
            'data-text': damage
        }));
    })
    // 데미지 마다 표시, 적 피격 모션 재생
    let damageShowClass = abilityDamages.length > 2 ? 'multiple-damage-show' : 'damage-show'
    abilityDamages.forEach(function (damage, damageIndex, damageArray) {
        let startDelay = abilityEffectDuration / damageArray.length * damageIndex;
        if (damageIndex === 0) {
            $enemyIdleVideo.addClass('hidden').get(0).pause()
            $enemyIdleVideo.get(0).currentTime = 0;
            $enemyDamagedVideo.removeClass('hidden');
        }
        setTimeout(function () {
            // 피격 비디오 연속재생의 경우 idle 과 교차재생하면 부자연스러워 지므로 연속재생후 마지막에 되돌림
            $enemyDamagedVideo.get(0).currentTime = 0; // 빼면 부자연스러워짐
            $enemyDamagedVideo.get(0).play();
            // 데미지 표시
            $('.ability-damage-wrapper .ability-damage-' + damageIndex).addClass(damageShowClass);
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

    // 버프 이펙트 처리
    let longestBuffDelay = processBuffEffect(addedBuffStatusesList, abilityEffectDuration);
    let buffEndTime = abilityEffectDuration + longestBuffDelay;

    // 디버프 이펙트 처리
    let debuffStartDelay = hasDebuff ? abilityEffectDuration + longestBuffDelay : abilityEffectDuration; // 버프 없으면 즉시시작
    let longestDebuffDelay = processDebuffEffect(addedDebuffStatusesList, debuffStartDelay);
    let debuffEndTime = debuffStartDelay + longestDebuffDelay;

    let totalEndTime = Math.max(abilityEffectDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processAbility] totalTime', totalEndTime, 'abilityDuration ', abilityEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 500));
}


function processEnemyAbility(responseAbilityData) {
    // 변수초기화
    let abilityData = responseAbilityData;
    let charOrder = abilityData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(abilityData.moveType);
    let abilityHitCount = abilityData.hitCount; // 히트수 (피격모션, 데미지 표시관련)
    let damages = abilityData.damages;
    let elementTypes = abilityData.elementTypes;
    let chargeGauges = abilityData.chargeGauges;
    let hps = abilityData.hps;
    let hpRates = abilityData.hpRates;
    // 적 한정
    let targetOrders = abilityData.enemyAttackTargetOrders;
    let isAllTarget = abilityData.allTarget; // 전체공격여부
    let isAllTargetSubstituted = isAllTarget && targetOrders.every(target => target === targetOrders[0]) // 전체공격, 모든타겟 동일한경우
    let isEnemyPowerUp = abilityData.enemyPowerUp;
    let isEnemyCtMax = abilityData.enemyCtMax;


    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = abilityData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    addedBuffStatusesList
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = abilityData.battleStatusList;
    // 아군 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList.some(arr => arr.length > 0);
    let hasDebuff = addedDebuffStatusesList.some(arr => arr.length > 0);

    // 준비 - 적
    let $abilityVideo = $('.enemy-video-container .' + moveType.className);
    let abilityDuration = $abilityVideo.get(0).duration * 1000 - 100;
    let abilityHitDuration = abilityDuration / abilityHitCount; // 히트당 길이는 데미지 표시용 1000ms 제외
    let $idleDefaultVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className); // 차지어택 종료후 idle_default
    let $standByIdleVideo = $('.enemy-video-container video:not(.hidden)');
    let audioSelector =
        isEnemyPowerUp ? '.global-audio-container .enemy-power-up' :
            isEnemyCtMax ? '.global-audio-container .enemy-ct-max ' : '.enemy-audio-container .' + moveType.className;
    console.log('ready', $abilityVideo, $idleDefaultVideo)

// EFFECT 이펙트 시작

    // 오디오 재생
    let enemyAudioPlayer = new AudioPlayer();
    let audioSrcs = $(audioSelector).toArray().map(element => element.src);
    enemyAudioPlayer.loadSounds(audioSrcs).then(() => {
        enemyAudioPlayer.playAllSounds();
    });

    //TODO 나중에 분리
    if (isEnemyPowerUp) {
        abilityDuration = $('.global-video-container .enemy-power-up').get(0).duration * 1000 / 2;
        $('.global-video-container .enemy-power-up').one('ended', function () {
            $(this).addClass('hidden');
        }).removeClass('hidden').get(0).play();
    }

    // TODO 아군 피격 보이스 재생


    // 적 서포트 어빌리티 이펙트 재생
    if (isEnemyPowerUp || isEnemyCtMax) {
        let videoSelector = isEnemyPowerUp ? '.global-audio-container .enemy-power-up' : '.global-audio-container .enemy-ct-max';
        let $effectVideo = $(videoSelector);
        abilityDuration = $effectVideo.get(0).duration; // 이펙트 길이로 갱신
        playVideo($effectVideo);
    } else {
        playVideo($abilityVideo, null, $standByIdleVideo);
    }

    //  데미지 마다 반복
    console.log('[processEnemyAbility] targetOrder = ', targetOrders, 'isAllTarget =', isAllTarget);
    damages.every(function (damage, damageIndex) {
        let effectDelay = isAllTarget ?
            abilityHitDuration : // 전체공격인 경우 이펙트 종료후 1회만 데미지 발생
            abilityHitDuration * damageIndex; // 단일 타겟의 경우 공격
        let targetOrder = isAllTarget ? targetOrders[damageIndex % targetOrders.length] : targetOrders[damageIndex]; // 데미지가 발생한 타겟순서, 전체타겟의 경우 1,2,3,4 만옴
        console.log('targetorder', targetOrder, 'effectdelay', effectDelay);

        if (isAllTargetSubstituted && damageIndex % targetOrders.length !== 0) {
            // 전체공격 이면서 감싸기 && 데미지가 해당 타수의 첫번째가 아닌경우 ex) targetOrders = [1, 1, 1, 1, 1, 1, 1, 1] 전체공격, 타겟 4명, 타수2회, 조건 진입은 index 가 1, 2, 3, 5, 6, 7
            let $attackDamage = $('.enemy-damage-wrapper .enemy-attack-damage.actor-' + targetOrder);
            $attackDamage.text(Number.parseInt($attackDamage.text()) + damage);
            let $additionalDamages = $attackDamage.find('.additional-damage:first');
            $additionalDamages.text(Number.parseInt($additionalDamages));
            return true; // 이후 처리 무시 (모션)
        }

        // 데미지 채우기 및 표시
        let $attackDamage = $('<div>', {
            class: 'damage enemy-attack-damage actor-' + targetOrder + ' element-type-' + elementTypes[damageIndex].toLowerCase(),
            text: damage
        });
        $attackDamage.appendTo($('.enemy-damage-wrapper'))
            .delay(effectDelay + 100 * damageIndex) // 이전 캐릭터보다 100ms 씩 딜레이
            .fadeTo(10, 1).delay(400)
            .fadeTo(400, 0, function () {
                $(this).remove();
            });

        // 아군의 피격 이펙트 재생
        let $targetIdleVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.IDLE.className);
        let $targetDamagedVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.DAMAGED.className);
        setTimeout(function () {
            $targetDamagedVideo.removeClass('hidden').get(0).play(); // 재생할때 순서별로 약간씩 딜레이 100 추가
            $targetIdleVideo.addClass('hidden'); // idle 보일경우 숨김

            // 아군 피격 모션을 idle 로 되돌림
            setTimeout(function () {
                $targetIdleVideo.removeClass('hidden');
                $targetDamagedVideo.addClass('hidden');
            }, isAllTarget ? 500 : abilityHitDuration - 100); // 이건 이미 이펙트별 딜레이가 적용되어잇으로 공격 횟수별로 걸어주면 됨, 전체공격이면 500ms 만
        }, effectDelay + 100 * damageIndex);

        return true;
    });

    // 스테이터스 아이콘 갱신
    processStatusIconSync(currentBattleStatusesList, abilityDuration);

    // 버프 이펙트 처리
    let longestBuffDelay = processBuffEffect(addedBuffStatusesList, abilityDuration);
    let buffEndTime = abilityDuration + longestBuffDelay;

    // 디버프 이펙트 처리
    let debuffStartDelay = hasDebuff ? abilityDuration + longestBuffDelay : abilityDuration; // 버프 없으면 즉시시작
    let longestDebuffDelay = processDebuffEffect(addedDebuffStatusesList, debuffStartDelay);
    let debuffEndTime = debuffStartDelay + longestDebuffDelay;

    let totalEndTime = Math.max(abilityDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processEnemyAbility] totalTime', totalEndTime, 'abilityDuration ', abilityDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 100));
}