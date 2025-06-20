function processAttack(responseAttackData) {
    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let totalHitCount = attackData.totalHitCount;
    let multiAttackCount = attackData.attackMultiHitCount;
    let damages = attackData.damages;
    let elementType = attackData.elementTypes[0];
    let attackHitCount = damages.length;
    let attackCount = attackHitCount / multiAttackCount; // 실제 싱글/더블/트리플 어택 카운트 (난격제외)
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
    // 준비 - 아군 (난격) : 난격이 붙은경우 이펙트, 모션을 전체적으로 천천히 재생한다.
    let playBackRate = 1.0 - 0.1 * (multiAttackCount - 1); // 난격시 느리게
    $attackEffectVideo.get(0).playbackRate = playBackRate;
    $attackMotionVideo !== null ? $attackMotionVideo.get(0).playbackRate = playBackRate : null;
    attackDuration = Math.floor(attackDuration / playBackRate); // 재생 속도에 따른 길이 보정
    let attackDelay = Math.floor(attackHitCount / multiAttackCount >= 3 ? Math.max((attackDuration - 400), 1100) / attackCount : attackDuration / attackCount); // 기본 타수당 딜레이, 난격에 다른 속도 지정후 계산 (약 350ms, 보통 평타 이펙트의 길이가 350 350 900 쯤 된다), 3타시 최소 1100ms 보장
    console.log('processAttack charOrder = ', charOrder, ' playbackRATE = ', playBackRate, ' attackDuration', attackDuration);
    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
    let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
    let damagedMoveClassName = standbyMoveClassName === 'none' ?
        MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
    let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

    // 오디오 재생
    let audioSrcs = [];
    audioSrcs.push($('.party-audio-container ' + partySelector + ' .' + moveType.className + ".voice").attr('src')); // 보이스
    let effectSrc = $('.party-audio-container ' + partySelector + ' .' + MoveType.SINGLE_ATTACK.className + '.effect').attr('src');
    audioSrcs.push(...Array(attackHitCount).fill(effectSrc)); // 타수만큼 싱글어택 사운드 준비
    let audioPlayer = new AudioPlayer();
    audioPlayer.loadSounds(audioSrcs).then(() => {
        audioPlayer.playAttackSounds(attackHitCount, multiAttackCount, attackDelay);
    });

    // 아군 일반공격 이펙트 재생
    playVideo($attackEffectVideo, $attackMotionVideo, $idleVideo);

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    let $attackDamageWrapper = $('<div>', {class: 'attack-damage-wrapper actor-' + charOrder});
    $('#damageContainer').append($attackDamageWrapper);

    const damageFragment = document.createDocumentFragment();
    damages.forEach(function (damage, index) {
        // 데미지 채우기
        let attackIndex = Math.floor(index / multiAttackCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % multiAttackCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)

        // 난격 여부 확인 후 클래스 설정 / [012 345 678] 3타 3난격 시 ap-0 m-0, ap-0 m-1, ap-0 m-2, ap-1 m-0, ap-1 m-1, ...
        let attackDamagePositionClassName = ' attack-damage-position-' + attackIndex + ' multi-' + multiAttackIndex;

        let $attackDamage = $('<div>', { // 본 공격 + 난격
            class: 'attack-damage actor-' + charOrder + ' element-type-' + elementType.toLowerCase() + attackDamagePositionClassName,
            text: damage,
        });
        damageFragment.append($attackDamage[0]);
        let $additionalDamage = $('<div>', { // 추격
            class: 'additional-damage-wrapper actor-' + charOrder + ' element-type-' + elementType.toLowerCase() + attackDamagePositionClassName,
            text: damage // 공간 사용을 위해
        }).append((additionalDamages[index] || []).map(additionalDamage =>
            $('<div>', {
                class: 'additional-damage element-type-' + elementType.toLowerCase(),
                text: additionalDamage
            })
        ))
        damageFragment.append($additionalDamage[0]);

        let startDelay = attackDelay * attackIndex; // 1타당 시작시 걸어줄 딜레이, 난격이 있을경우 난격마다 딜레이가 같아짐 ([1,2,3,4,5,6] 2회난격시 승수가 0, 0, 1, 1, 2, 2)
        let multiHitDelay = multiAttackIndex * 115; // 난격마다 추가 딜레이
        let totalDelay = startDelay + multiHitDelay; // 최종 딜레이
        // console.log('[processAttack] baseDelay = ', attackDelay, ' startDelay = ', startDelay, ' multiHitDealy = ', multiHitDelay, ' totalDelay = ', totalDelay);

        // 마지막에 데미지 요소 추가
        index >= attackHitCount - 1 ? $attackDamageWrapper.append(damageFragment) : null;

        setTimeout(function () {
            // 적 피격 이펙트 재생 (난격일땐 재생 x)
            multiAttackIndex === 0 ? playVideo($enemyDamagedVideo, null, $enemyIdleVideo) : null;
            // 데미지 표시
            $attackDamage.addClass('damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('damage-show'); // 추가데미지는 부모와의 fadeIn 겹침을 피하기 위해 display none 설정되어있음
                }, (index + 1) * 100);
            });

            if (index >= attackHitCount - 1) {
                // 데미지 제거 데미지갯수 * 2 (추격컨테이너 까지 지움)
                setTimeout(function () {
                    $('.attack-damage-wrapper.actor-' + charOrder).slice(0, attackHitCount * 2).remove();
                }, 1300); // 마지막 데미지 페이드아웃 딜레이 대기, 현재 1200ms
            }
        }, totalDelay)
    })

    // 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = attackDuration;
    console.log("[processAttack] totalEndTime = " + totalEndTime)
    // 최종종료
    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 300));
}


function processEnemyAttack(responseAttackData) {
    // 변수초기화
    let attackData = responseAttackData;
    let charOrder = attackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(attackData.moveType);
    let attackHitCount = moveType === MoveType.SINGLE_ATTACK ? 1 : moveType === MoveType.DOUBLE_ATTACK ? 2 : 3;
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
    // let $enemyAttackVideo = $('.enemy-video-container .' + moveType.className);
    let $enemyAttackVideo = $('.enemy-video-container .' + MoveType.SINGLE_ATTACK.className);
    let attackDuration = $enemyAttackVideo.get(0).duration * 1000 * attackHitCount; // ms 변환 및 100ms 영상보정
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
    $enemyAttackVideo.removeClass('hidden');
    $enemyIdleVideo.addClass('left-hidden');
    $enemyIdleVideo.get(0).pause();
    $enemyIdleVideo.get(0).currentTime = 0;
    for (let i = 0; i < attackHitCount; i++) {
        setTimeout(function () {
            $enemyAttackVideo.get(0).currentTime = 0;
            $enemyAttackVideo.get(0).play();

            if (i === attackHitCount - 1) {
                setTimeout(function () {
                    requestAnimationFrame(function () {
                        $enemyIdleVideo.removeClass('left-hidden');
                        $enemyIdleVideo.get(0).play();
                        setTimeout(function () {
                            $enemyAttackVideo.addClass('hidden');
                        }, 50)
                    })
                }, attackDuration / attackHitCount)
            }
        }, (attackDuration / attackHitCount) * i)
    }


    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    let attackHitDuration = attackDuration / attackHitCount; // 1타가 사용할 길이
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
    }, totalEndTime + 300));

// 현재 일반공격의 스테이터스 갱신은 없음
}
