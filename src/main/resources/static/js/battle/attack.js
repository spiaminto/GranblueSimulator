function processAttack(responseAttackData) {
    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let hitCount = attackData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let damages = attackData.damages;
    let elementType = attackData.elementTypes[0];
    let attackHitCount = damages.length;
    let additionalDamages = attackData.additionalDamages;
    let additionalAttackHitCount = additionalDamages.reduce((totalSize, damage) => totalSize + damage.length, 0);
    let chargeGauges = attackData.chargeGauges;
    let hps = attackData.hps;
    let hpRates = attackData.hpRates;

    // 준비 - 아군
    let $attackEffectVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.effect');
    let $attackMotionVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.motion');
    $attackMotionVideo = $attackMotionVideo.length <= 0 ? null : $attackMotionVideo; // 주인공은 모션 이펙트 분리, 나머지는 통합
    let $idleVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);
    let attackDuration = $attackEffectVideo.get(0).duration * 1000;
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
    let audioSrcs = [];
    audioSrcs.push($('.party-audio-container ' + partySelector + ' .' + moveType.className + ".voice").attr('src')); // 보이스
    let effectSrc = $('.party-audio-container ' + partySelector + ' .' + moveType.className + '.effect').attr('src');
    audioSrcs.push(effectSrc); // 이펙트
    if (additionalDamages[0].length >= 2) {
        audioSrcs.push(...Array(additionalDamages[0].length - 1).fill(effectSrc)); // 추격 추가 이펙트
    }
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSounds(audioSrcs).then(() => {
        audioPlayer.playAttackSounds();
    });

    // 아군 일반공격 이펙트 재생
    playVideo($attackEffectVideo, $attackMotionVideo, $idleVideo);

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    let $attackDamageWrapper = $('<div>', {class: 'attack-damage-wrapper actor-' + charOrder});
    $('#damageContainer').append($attackDamageWrapper);

    damages.forEach(function (damage, index) {
        // 데미지 채우기
        let $additionalDamage;
        let $attackDamage;
        $attackDamage = $('<div>', { // 일반공격
            class: 'attack-damage actor-' + charOrder + ' element-type-' + elementType.toLowerCase(),
            text: damage
        });
        $additionalDamage = $('<div>', { // 추격
            class: 'additional-damage-wrapper actor-' + charOrder + ' element-type-' + elementType.toLowerCase(),
            text: damage // 공간 사용을 위해
        }).append((additionalDamages[index] || []).map(additionalDamage =>
            $('<div>', {
                class: 'additional-damage element-type-' + elementType.toLowerCase(),
                text: additionalDamage
            })
        ));
        $attackDamageWrapper.append($attackDamage, $additionalDamage);

        let startDelay = attackDuration / damages.length * index;
        startDelay = index !== 0 && damages.length === 3 ? startDelay - 150 : startDelay; // 평타의 길이가 보통 300, 300, 900 정도 이므로 2타부터 보정
        setTimeout(function () {
            // 적 피격 이펙트 재생
            playVideo($enemyDamagedVideo, null, $enemyIdleVideo);
            // 데미지 표시
            $attackDamage.addClass('damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('damage-show'); // 추가데미지는 부모와의 fadeIn 겹침을 피하기 위해 display none 설정되어있음
                }, (index + 1) * 100);
            });

            if (index >= damages.length - 1) {
                // 데미지 제거 데미지갯수 * 2 (추격컨테이너 까지 지움)
                setTimeout(function () {
                    $('.attack-damage-wrapper.actor-' + charOrder).slice(0, damages.length * 2).remove();
                }, 1000); // 마지막 데미지 페이드아웃 딜레이 대기
            }
        }, startDelay)
    })

    // 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = attackDuration;
    console.log("[processAttack] total = " + totalEndTime)
    // 최종종료
    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 100));
}


function processEnemyAttack(responseAttackData) {
    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let hitCount = moveType === MoveType.SINGLE_ATTACK ? 1 : moveType === MoveType.DOUBLE_ATTACK ? 2 : 3;
    let damages = attackData.damages;
    let elementTypes = attackData.elementTypes;
    let additionalDamages = attackData.additionalDamages;
    let chargeGauges = attackData.chargeGauges;
    let hps = attackData.hps;
    let hpRates = attackData.hpRates;
    // 적 한정
    let targetOrders = attackData.enemyAttackTargetOrders;
    let uniqueTargetOrders = [...new Set(targetOrders)];
    let isAllTarget = attackData.allTarget; // 전체공격여부

    // 준비
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let $enemyAttackVideo = $('.enemy-video-container .' + moveType.className);
    let attackDuration = $enemyAttackVideo.get(0).duration * 1000; // ms 변환 및 100ms 영상보정
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    // 준비 - 아군
    let $partyDamagedVideos = $('.party-video-container .' + MoveType.DAMAGED.className); // 문서순서 = order 순서
    let $partyIdleVideos = $('.party-video-container .' + MoveType.IDLE.className);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + moveType.className).attr('src');
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSound(audioSrc).then(() => {
        audioPlayer.playAllSounds();
    });

    // 적 일반공격 이펙트 재생
    playVideo($enemyAttackVideo, null, $enemyIdleVideo);


    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    let attackHitDuration = attackDuration / hitCount; // 1타가 사용할 길이
    damages.forEach(function (damage, index) {
        let startDelay = isAllTarget ?
            attackHitDuration * (index / targetOrders.length) :
            attackHitDuration * index;
        let targetOrder = isAllTarget ?
            targetOrders[index % targetOrders.length] :
            targetOrders[index];
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

        // 데미지 표시
        setTimeout(function () {
            // 데미지 및 표시
            $attackDamage.addClass('enemy-damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
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
        }, startDelay)
    })

    let totalEndTime = isAllTarget ? attackDuration + 100 : attackDuration * damages.length + 100;
    console.log("[processEnemyAttack] total = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        // 최종종료
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log('ENEMY ATTACK done');
        resolve();
    }, totalEndTime + 100));

// 현재 일반공격의 스테이터스 갱신은 없음
}
