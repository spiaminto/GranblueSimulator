async function processChargeAttack(responseChargeAttackData) {
    // 변수초기화
    let chargeAttackData = responseChargeAttackData;
    let charOrder = chargeAttackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(chargeAttackData.moveType);
    let chargeAttackHitCount = chargeAttackData.length; // 히트수 (피격모션, 데미지 표시관련)
    let damages = chargeAttackData.damages;
    let elementType = chargeAttackData.elementTypes[0];
    let chargeGauges = chargeAttackData.chargeGauges;
    let hps = chargeAttackData.hps;
    let hpRates = chargeAttackData.hpRates;
    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = chargeAttackData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = chargeAttackData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = chargeAttackData.battleStatusList;

    // 준비 - 아군
    let $chargeAttackVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.effect');
    let $chargeAttackMotionVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.motion');
    $chargeAttackMotionVideo = $chargeAttackMotionVideo.length <= 0 ? null : $chargeAttackMotionVideo; // 주인공은 모션 이펙트 분리, 나머지는 통합
    let $idleMotionVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);
    let chargeAttackDuration = $chargeAttackVideo.get(0).duration * 1000 - 100 + 1000; // 아군 오의 이펙트 종료 후 데미지 표시를 위해 + 1000
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

// EFFECT 이펙트 시작

    // 오디오 재생
    let charAudioPlayer = new AudioPlayer();
    let audioInfos = $('.party-audio-container ' + partySelector + ' .' + moveType.className).toArray().map(element => element.src);
    charAudioPlayer.loadSounds(audioInfos).then(() => {
        charAudioPlayer.playAllSounds()
    });

    // 아군 오의 이펙트 재생
    playVideo($chargeAttackVideo, $chargeAttackMotionVideo, $idleMotionVideo);

    // 데미지 채우기
    $('.charge-attack-damage-wrapper').append($('<div>', {
        class: 'charge-attack-damage element-type-' + elementType.toLowerCase(),
        text: damages[0]
    }));
    // 이펙트 종료 직전부터 데미지, 피격이펙트 재생
    let chargeAttackEffectHitDelay = chargeAttackDuration - 250; // 히트 시점을 끝나기 0.25초전으로
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyDamagedVideo, null, $enemyIdleVideo);
        // 화면 흔들기
        $('#videoContainer').addClass('push-left-down-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('push-left-down-effect');
        }, 150);
        // 데미지 표시 및 제거
        $('.charge-attack-damage-wrapper .charge-attack-damage').addClass('damage-show');
        setTimeout(function () {
            $('.charge-attack-damage-wrapper').empty();
        }, 1000);
    }, chargeAttackEffectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(currentBattleStatusesList, chargeAttackDuration);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, chargeAttackDuration + 500);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(chargeAttackDuration, buffEndTime, debuffEndTime);
    console.log('[processChargeAttack] chargeAttackDuration ', chargeAttackDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime, 'totalEndTime = ', totalEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}

async function processEnemyChargeAttackPreEffect() {
    let $chargeAttackPreEffectVideo = $('.global-video-container .enemy-charge-attack-start');
    playVideo($chargeAttackPreEffectVideo, null, null);
    $('.global-audio-container .enemy-charge-attack-start').get(0).play();

    return new Promise(resolve => setTimeout(function () {
        console.log('[processEnemyChargeAttackPreEffect] DONE');
        resolve();
    }, $chargeAttackPreEffectVideo.get(0).duration * 1000));
}

async function processEnemyChargeAttack(responseChargeAttackData) {
    await processEnemyChargeAttackPreEffect();
    // 변수초기화
    let chargeAttackData = responseChargeAttackData;
    let charOrder = chargeAttackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(chargeAttackData.moveType);
    let totalHitCount = chargeAttackData.totalHitCount; // 히트수 (피격모션, 데미지 표시관련)
    let damages = chargeAttackData.damages;
    let additionalDamages = chargeAttackData.additionalDamages;
    let hitCount = damages.length;
    let elementTypes = chargeAttackData.elementTypes;
    let chargeGauges = chargeAttackData.chargeGauges;
    let hps = chargeAttackData.hps;
    let hpRates = chargeAttackData.hpRates;
    // 적 한정
    let targetOrders = chargeAttackData.enemyAttackTargetOrders;
    let isAllTarget = chargeAttackData.allTarget; // 전체공격여부
    let uniqueTargetOrders = [...new Set(targetOrders)];


    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = chargeAttackData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 지워진 스테이터스 효과
    let removedBattleStatusList = chargeAttackData.removedBattleStatusList;
    let removedBuffStatusesList = removedBattleStatusList.map(removedBattleStatuses => removedBattleStatuses.filter(status => status.type === 'BUFF'));
    let removedDebuffStatusesList = removedBattleStatusList.map(removedDebuffStatuses => removedDebuffStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = chargeAttackData.battleStatusList;

    // 준비
    let $chargeAttackVideo = $('.enemy-video-container .' + moveType.className);
    let effectHitDelay = Number($chargeAttackVideo.attr('data-effect-hit-delay')); // 이펙트 시작 ~ 데미지 표시 까지 딜레이
    let chargeAttackDuration = $chargeAttackVideo.get(0).duration * 1000;
    let chargeAttackHitDuration = (chargeAttackDuration - effectHitDelay) / hitCount; // 1타가 사용할 길이
    let $idleDefaultVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className); // 차지어택 종료후 idle_default
    let standbyIdleClassName = MoveType.byClassName($('.enemy-video-container').attr('data-standby-move-class')).getIdleType().className;
    let $standbyIdleVideo = $('.enemy-video-container .' + standbyIdleClassName);
    // 준비 - 아군
    let $partyDamagedVideos = $('.party-video-container .' + MoveType.DAMAGED.className); // 문서순서 = order 순서
    let $partyIdleVideos = $('.party-video-container .' + MoveType.IDLE.className);

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').attr('data-standby-move-class', MoveType.NONE.className);
    // 전조 컨테이너 deActivate
    setTimeout(function () {
        $(".omen-container").removeClass('activated')
            .find('.omen-text').removeClass().addClass('omen-text') // 해당 컨디션 찾기 귀찮아서 그냥 밀어버리고 omen-text 추가
            .find('.omen-prefix').text('')
            .find('.omen-value').text('');
    }, chargeAttackDuration);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + moveType.className).attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 적 일반공격 이펙트 재생 (스탠바이 -> 차지어택 -> idle Default순)
    $standbyIdleVideo.addClass('hidden').get(0).pause(); // 스탠바이 정지, 숨김
    playVideo($chargeAttackVideo, null, $idleDefaultVideo);

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    damages.forEach(function (damage, index) {
        let startDelay = isAllTarget ? 0 : chargeAttackHitDuration * index; // 오의는 전체공격시 1타뿐이므로 0 (+ efectHitDelay)
        let targetOrder = isAllTarget ? targetOrders[index % targetOrders.length] : targetOrders[index];
        // 데미지 삽입
        let $enemyDamageWrapper = $('.enemy-damage-wrapper.actor-' + targetOrder).last(); // 이전 행동 공격데미지와 겹치지 않도록 추가한 래퍼 사용
        let $attackDamage = $('<div>', {
            class: 'damage enemy-attack-damage actor-' + targetOrder + ' element-type-' + elementTypes[index].toLowerCase(),
            text: damage
        });
        let $additionalDamage = $('<div>', {
            class: 'damage enemy-additional-damage-wrapper actor-' + targetOrder + ' element-type-' + elementTypes[index].toLowerCase(),
            text: damage
        }).append((additionalDamages[index] || []).map(additionalDamage =>  // 추격이 존재하면 붙임
            $('<div>', {
                class: 'damage additional-damage' + ' element-type-' + elementTypes[index].toLowerCase(),
                text: additionalDamage
            })
        ))
        $enemyDamageWrapper.append($attackDamage, $additionalDamage);

        setTimeout(function () {
            // console.log('[processEnemyChargeAttack] index = ', index, ' startDelay = ', startDelay, ' effectHitDelay = ', effectHitDelay);
            // 데미지 표시
            $attackDamage.addClass('enemy-damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('enemy-damage-show');
                }, (index + 1) * 100);
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
    processStatusIconSync(currentBattleStatusesList, chargeAttackDuration);

    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, chargeAttackDuration + 500);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(chargeAttackDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processEnemyChargeAttack] chargeAttackDuration ', chargeAttackDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime, 'totalEndTime = ', totalEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 200));
}