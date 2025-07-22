/**
 * 적의 데미지 발생관련 후처리
 * 적은 일반공격, (서포트)어빌리티, 차지어택의 데미지 표시방식 및 아군 피격처리가 거의 동일하므로 통합하여 사용
 * @param response
 * @param $enemyVideo nullable, null 일시 effectDuration 이 0으로 set됨
 * @param $partyVideos
 */
function enemyDamagesPostProcess(response, $enemyVideo, $partyVideos) {
    let attackCount =
        response.moveType === MoveType.SINGLE_ATTACK ? 1 :
            response.moveType === MoveType.DOUBLE_ATTACK ? 2 :
                response.moveType === MoveType.TRIPLE_ATTACK ? 3 : 0;
    let isAllTarget = response.moveType !== MoveType.TURN_END_PROCESS
        ? response.allTarget
        : response.totalHitCount >= 2 // 턴종 데미지시 데미지가 2회이상 발생하면 allTarget 으로 간주 (캐릭터별 딜레이 적용 위함)
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];
    if (isAllTarget && uniqueTargetOrders.length === 1) uniqueTargetOrders = Array($('.party-video-wrapper').length).fill(uniqueTargetOrders[0]); // 전체공격 감싸기 상황. 파티 인원만큼 감싸기 캐릭터의 order 늘림. CHECK 파티 인원 구하는 방식은 차후에 더 나은방법이 있으면 수정
    let effectDuration = $enemyVideo?.effect ? $enemyVideo.effect.get(0).duration * 1000 : 0;
    let effectHitDelay = Number($enemyVideo?.effect?.attr('data-effect-hit-delay')) || 0; // 이펙트 시작 ~ 데미지 표시 까지 딜레이, 없으면 0
    let effectHitDuration = attackCount === 0
        ? (effectDuration - effectHitDelay) / response.damages.length // 어빌리티, 오의시 1타가 사용할 길이
        : effectDuration; // 일반공격시 1타가 사용할 길이 (일반공격은 singleAttack * 3회 재생하므로 그대로 사용)

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $enemyDamageWrappers = new Map();
    uniqueTargetOrders.forEach(targetOrder => {
        $enemyDamageWrappers.set(targetOrder, $('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder})); // CHECK 캐릭터랑 다르게 순서정보 없음. 차후 필요시 추가
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    response.damages.forEach(function (damage, index) {
        let startDelay = isAllTarget
            ? effectHitDuration * (Math.floor(index / uniqueTargetOrders.length)) + (index % uniqueTargetOrders.length * 100) // 전체공격시 1타 길이로 중복제거한 타겟수만큼 끊어서 시작 딜레이 설정 (123 / 123 / 123...), 타겟별로 100ms 씩 추가 딜레이
            : effectHitDuration * index; // 전체공격 아니면 index 만큼 1타 길이 사용
        let targetOrder = response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $enemyDamageWrappers.get(targetOrder);
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);

        // 마지막에 돔에 추가
        index >= response.damages.length - 1 ? $('#enemyAttackDamageContainer').append(Array.from($enemyDamageWrappers.values())) : null;

        setTimeout(function () {
            // console.log('[enemyDamagePostProcess] index = ', index, ' startDelay = ', startDelay, ' effectHitDelay = ', effectHitDelay, ` attackCount = ${attackCount}`);
            // 데미지 표시
            $damageElements.$damage.addClass('enemy-damage-show');
            // 추가데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('enemy-damage-show');
                }, (index + 1) * 50);
            });
            // 아군 피격 재생
            playVideo($partyVideos[targetOrder].effect, null, $partyVideos[targetOrder].idle);
            // 마지막 공격시 종료후 데미지 삭제.
            if (index >= response.damages.length - 1) {
                setTimeout(function () {
                    $enemyDamageWrappers.values().forEach(function (wrapper) {
                        $(wrapper).remove();
                    })
                }, 1000);
            }
        }, startDelay + effectHitDelay)
    })
}

/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param charOrder
 * @param elementType
 * @param type 데미지 타입, 'attack', 'ability', chargeAttack'
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemyDamage optional boolean
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(charOrder, elementType, type, index, damage, additionalDamages, isEnemyDamage = false) {
    let typeClassName;
    switch (type) {
        case 'attack':
            typeClassName = ' attack-damage';
            break;
        case 'ability':
            typeClassName = ' ability-damage';
            break;
        case 'chargeAttack':
            typeClassName = ' charge-attack-damage';
            break;
        default:
            new Error('[getDamageElement] invalid type, type = ' + type);
    }
    let actorClassName = ' actor-' + charOrder;
    let elementClassName = ' element-type-' + elementType.toLowerCase();
    let damageIndexClassName = ' damage-index-' + index;
    let enemyClassName = isEnemyDamage ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';


    // 데미지 요소
    let $damage = $('<div>', {
        class: typeClassName + actorClassName + elementClassName + damageIndexClassName + elementClassName + missClassName + enemyClassName,
        text: damage
    });
    // 추격 요소
    let $additionalDamage = $('<div>', {
        class: 'additional-damage-wrapper ' + enemyClassName + actorClassName + elementClassName + missClassName,
        text: additionalDamages
    }).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $('<div>', {
            class: 'additional-damage' + elementClassName,
            text: additionalDamage
        })
    ));
    return {$damage, $additionalDamage};
}


/**
 * 커맨드 패널의 현재 스테이터스 아이콘 갱신 (이펙트 종료 직후)
 * @param currentBattleStatusesList 갱신할 현재 스테이터스리스트
 * @param effectVideoDuration 이펙트 길이 (시작딜레이로 사용)
 */
function processStatusIconSync(currentBattleStatusesList, effectVideoDuration) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    setTimeout(function () {
        currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
            let $statusContainer = $('.status-container.actor-' + actorIndex);
            $statusContainer.find('.status').remove(); // 스테이터스 비움
            currentBattleStatuses.forEach(function (status, index) {
                // 어빌리티 패널에 갱신된 스테이터스 추가
                let displayClassName = index > 0 && (
                    currentBattleStatuses[index - 1].name === status.name // 이름이 같거나
                    || currentBattleStatuses[index - 1].imageSrc === status.imageSrc //아이콘 같으면
                ) ? 'd-none' : ''; // 안보이게 설정
                let $statusInfo = $('<div>', {
                    class: 'status status',
                    'data-status-type': status.type
                }).addClass(displayClassName)
                    .append(
                        $('<img>', {
                            src: status.imageSrc,
                            class: 'status-icon' + (status.imageSrc.length < 1 ? ' none-icon' : '')
                        }),
                        $('<div>', {class: 'status-name d-none', text: status.name}),
                        $('<div>', {class: 'status-info-text d-none', text: status.statusText}),
                        $('<div>', {class: 'status-duration d-none', text: status.duration})
                    );
                $statusContainer.append($statusInfo);
            })
        });
    }, effectVideoDuration);
}

function processHealEffect(healArray, effectVideoDuration) {
    let healDelay = effectVideoDuration;
    if (healArray.length === 0 || healArray.reduce((a, b) => a + b, 0) === 0) return healDelay;
    let audioPlayer = new AudioPlayer().init();
    let healEffectDuration = 500;

    let $enemyDamageWrappers = new Map(); // 적의 데미지 컨테이너에 힐 표시 래퍼를 추가하여 사용
    healArray.forEach(function (heal, actorIndex) {
        // console.log('[processHealEffect] heal = ', heal, ' actorIndex = ', actorIndex);
        if (heal !== 0) {
            $enemyDamageWrappers.set(actorIndex, $('<div>', {class: 'enemy-damage-wrapper actor-' + actorIndex}));
            let currentAttackDamageWrapperIndex = $('.attack-damage-wrapper').length;
            let $attackDamageWrapper = $('<div>', {class: 'ability-damage-wrapper ability-index-' + currentAttackDamageWrapperIndex});
            let startDelay = effectVideoDuration + (100 * actorIndex); // 캐릭터 순서대로 100씩 딜레이

            setTimeout(function () {
                // 이펙트 재생
                let $healVideo = $(`.global-effect-video-wrapper.actor-${actorIndex} .global-effect-video`);
                $healVideo.attr('src', GlobalSrc.HEAL.video);
                playVideo($healVideo, null, null);
                // 사운드 재생
                audioPlayer.loadAndPlay(GlobalSrc.HEAL.audio);
                // });
                // 데미지(힐 수치) 채우기 및 돔추가
                let isEnemyDamage = actorIndex !== 0; // 아군인 경우 적의 공격데미지 클래스를 사용하기 위함
                let $healWrapper = actorIndex === 0 ? $attackDamageWrapper : $enemyDamageWrappers.get(actorIndex); // 힐 수치가 들어갈 래퍼선택
                let $damageContainer = actorIndex === 0 ? $('#attackDamageContainer') : $('#enemyAttackDamageContainer'); // 실제 추가할 컨테이너
                let $damageElements = getDamageElement(0, 'NONE', 'attack', 0, heal, [], isEnemyDamage);
                $healWrapper.append($damageElements.$damage.addClass('heal heal-show')).appendTo($damageContainer);
            }, startDelay);

            healDelay = startDelay + healEffectDuration; // 버프 이펙트 시작할 딜레이 지정

            if (actorIndex === healArray.length - 1) { // 힐 (데미지 요소) 삭제 (적과 아군 동시 힐 발생까지 고려)
                setTimeout(function () {
                    $attackDamageWrapper.remove();
                    $enemyDamageWrappers.values().forEach(function (wrapper) {
                        $(wrapper).remove();
                    })
                }, startDelay + 1000);
            }
        }
    });
    return healDelay;
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @param effectVideoDuration 이펙트 비디오 길이 (시작 전 딜레이)
 * @returns longestBuffEndTime 가장 긴 버프 끝시간 (다음 시작 딜레이로 사용)
 */
function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, effectVideoDuration) {
    let lastBuffFadeoutStartTime = effectVideoDuration; // 버프 없을땐 이전 딜레이 (이펙트 딜레이) 만큼
    let buffShowDuration = 1000; // 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms
    let statusesList = addedBuffStatusesList.map((_, idx) => {
            let removedDebuffs = (removedDebuffStatusesList[idx] || []).map(debuff => ({
                value: debuff,
                type: 'removedDebuffs'
            }));
            // 버프는 같은 스테이터스가 여러개로 나뉘는 경우가 존재하므로 표시할때 중복제거
            let addedBuffes = (addedBuffStatusesList[idx] || []).map(buff => ({value: buff, type: 'addedBuffs'}));
            addedBuffes = [...new Map(addedBuffes.map(statusObject => [statusObject.value.name, statusObject])).values()];
            let removedBuffes = (removedBuffStatusesList[idx] || []).map(buff => ({value: buff, type: 'removedBuffs'}));
            removedBuffes = [...new Map(removedBuffes.map(statusObject => [statusObject.value.name, statusObject])).values()];
            return [...removedDebuffs, ...addedBuffes, ...removedBuffes];
        }
    );
    // console.log('[processBuffEffect] statusesList = {}', statusesList);
    // 표시 순서 removedDebuff -> addedBuff -> removedBuff
    let audioPlayer = new AudioPlayer().init();
    statusesList.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        if (statuses.length === 0) return;
        let currentEffectContainerIndex = $('.status-effect-wrapper.actor-' + actorIndex).length;
        let $statusEffectWrapper = $('<div>', {class: 'status-effect-wrapper actor-' + actorIndex + ' status-index-' + currentEffectContainerIndex}).appendTo($('.status-effect-container'))
        $statusEffectWrapper.addClass(actorIndex === 0 ? 'enemy' : 'party');
        statuses.forEach(function (statusObject, statusIndex) {
            let status = statusObject.value;
            let statusTypeName = status.type.toLowerCase();
            statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(status.name) ? 'none' : statusTypeName;
            let $statusEffect = $('<div>', {class: `status-effect status-effect-index-${statusIndex} ${statusTypeName}`})
                .append(
                    $('<img>', {src: status.imageSrc, class: status.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: status.effectText})
                );
            $statusEffectWrapper.append($statusEffect);

            let type = statusObject.type;
            let $statusRemovedEffect = null;
            if (type === 'removedDebuffs' || type === 'removedBuffs') {
                let statusEffectPosition = $statusEffect.position();
                $statusRemovedEffect = $('<div>', {class: 'status-effect status-removed-effect'}).css({
                    top: statusEffectPosition.top - 5,
                    left: statusEffectPosition.left - 10,
                    width: $statusEffect.find('.status-effect-text').width() + 30, // img 크기까지
                    height: $statusEffect.find('.status-effect-text').height()
                });
                $statusEffectWrapper.append($statusRemovedEffect);
            }

            let statusForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let statusPageCount = Math.floor(statusIndex / statusForPage); // 현재 표시할 스테이터스의 페이지 (0 부터)
            let startDelay = effectVideoDuration + (buffShowDuration * statusPageCount) + (150 * (statusIndex % statusForPage)); // 이펙트당 buffShowDuration 만큼 보여줌
            lastBuffFadeoutStartTime = startDelay + 800; // 가장 늦게 시작하는 버프가 fadeout 하는 시점

            setTimeout(() => {
                if ($statusRemovedEffect != null) {
                    // 스테이터스 제거 효과
                    audioPlayer.loadAndPlay(GlobalSrc.STATUS_REMOVED.audio);
                    $statusEffect.addClass('status-removed');
                    $statusRemovedEffect.addClass('active');
                } else {
                    $statusEffect.fadeTo(100, 1).delay(700).fadeTo(200, 0); // CHECK buffShowDuration 과 맞춰야함
                }

                if (statusIndex % statusForPage + 1 === statusForPage) {
                    // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, statusIndex % statusForPage + 1).remove(), 1100);
                }

                if (statusIndex >= statuses.length - 1) {
                    // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), buffShowDuration);
                }
            }, startDelay);
        });
    });
    return lastBuffFadeoutStartTime;
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @param debuffStartDelay 시작딜레이 (이펙트딜레이 + 버프딜레이)
 * @returns longestDebuffEndTime 가장 긴 디버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processDebuffEffect(addedDebuffStatusesList, debuffStartDelay) {
    let lastDebuffFadeoutStartTime = debuffStartDelay; // 디버프 없을댄 이전 딜레이 (이펙트 + 버프 딜레이) 만큼
    let debuffShowDuration = 1000; // 디버프 버프이펙트 fadeTo ~ fadeOut 까지 총 duration, ms
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [적][아][아][아][아]
        if (addedDebuffStatuses.length === 0) return;
        let currentEffectContainerIndex = $('.status-effect-wrapper.actor-' + actorIndex).length;
        let $statusEffectWrapper = $('<div>', {class: 'status-effect-wrapper actor-' + actorIndex + ' status-index-' + currentEffectContainerIndex}).appendTo($('.status-effect-container'))
        $statusEffectWrapper.addClass(actorIndex === 0 ? 'enemy' : 'party');

        addedDebuffStatuses.forEach(function (debuffStatus, debuffIndex) {
            let statusTypeName = debuffStatus.type.toLowerCase();
            statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(debuffStatus.name) ? 'none' : statusTypeName;
            let $statusEffect = $('<div>', {class: `status-effect status-effect-${debuffIndex} ${statusTypeName}`})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $statusEffectWrapper.append($statusEffect);

            let debuffForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 디버프 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let debuffPageCount = Math.floor(debuffIndex / debuffForPage); // 현재 표시할 디버프의 페이지 (0 부터)
            let startDelay = debuffStartDelay + (debuffShowDuration * debuffPageCount) + (150 * (debuffIndex % debuffForPage)); // 이펙트당 debuffShowDuration 만큼 보여줌
            lastDebuffFadeoutStartTime = debuffStartDelay + 800; // 마지막 디버프가 페이드아웃 시작하는 시점

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(700).fadeTo(200, 0); // CHECK debuffShowDuration 이랑 맞춰야함

                if (debuffIndex % debuffForPage + 1 === debuffForPage) {
                    // 스테이터스 페이지 마지막 마다 페이지 제거
                    setTimeout(() => $statusEffectWrapper.find('.status-effect').slice(0, debuffIndex % debuffForPage + 1).remove(), 1100);
                }

                if (debuffIndex >= addedDebuffStatuses.length - 1) {
                    // 마지막 스테이터스일시 래퍼 통째로 제거
                    setTimeout(() => $statusEffectWrapper.remove(), debuffShowDuration);
                }
            }, startDelay);
        });
    });
    return lastDebuffFadeoutStartTime;
}

/**
 * 파라미터로 넘어온 actorOrder, moveType, idleType 에 맞는 비디오 찾아서 오브젝트로 반환
 * @param actorOrder
 * @param moveType
 * @param idleType optional, 아군의 경우 없어도됨. 적의경우 idle default 가 아닌경우 사용
 * @returns {{motion: (jQuery|HTMLElement|*), effect: (jQuery|HTMLElement|*), idle: (jQuery|HTMLElement|*)}}, 없으면 null 로 대체됨
 */
function getVideo(actorOrder, moveType, idleType = MoveType.IDLE_DEFAULT) {
    let videoContainer = actorOrder === 0 ? $('.enemy-video-container') : $('.party-video-container');
    let $effectVideo = videoContainer.find('.actor-' + actorOrder + '.' + moveType.className + '.effect').eq(0);
    let $motionVideo = videoContainer.find('.actor-' + actorOrder + '.' + moveType.className + '.motion').eq(0);
    let $idleVideo = videoContainer.find('.actor-' + actorOrder + '.' + idleType.className).eq(0);
    // 없으면 null 로 대체
    $effectVideo = $effectVideo.length > 0 ? $effectVideo : null;
    $motionVideo = $motionVideo.length > 0 ? $motionVideo : null;
    $idleVideo = $idleVideo.length > 0 ? $idleVideo : null;
    return {
        effect: $effectVideo,
        motion: $motionVideo,
        idle: $idleVideo
    }
}

/**
 * 적 피격 동영상용 별도 getVideo
 * @returns {{motion: (Window.jQuery|HTMLElement|*), effect: (Window.jQuery|HTMLElement|*), idle: (Window.jQuery|HTMLElement|*)}}
 */
function getEnemyDamagedVideo() {
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let damagedMoveType = standbyMoveClassName === 'none' ? MoveType.DAMAGED_DEFAULT : MoveType.byClassName(standbyMoveClassName).getDamagedType();
    return getVideo(0, damagedMoveType, idleMoveType);
}


/**
 * 비디오를 재생하는 메서드
 * effectVideo, motionVideo 는 hidden 없앤 후 재생, 완료후 hidden
 * idleVideo 는 left-hidden 으로 왼쪽으로 뺀 후 재생 완료후 다시 돌려놓음
 *
 * CHECK 여기서 한번 더 분기필요하면 아얘 분리
 * @param $effectVideo
 * @param $motionVideo optional
 * @param $idleVideo optional
 */
function playVideo($effectVideo, $motionVideo, $idleVideo) {
    // console.log('[playVideo] $effectVideo = ', $effectVideo, ' $motionVideo = ', $motionVideo, ' $idleVideo = ', $idleVideo)
    if ($effectVideo == null) {
        console.warn('effectVideo is null');
        return; // 이펙트 비디오 없으면 무시
    }
    if ($effectVideo?.length === 0 || $motionVideo?.length === 0 || $idleVideo?.length === 0) {
        throw new Error('video length is 0') // 비디오는 있거나, null 이거나 둘중하나
    }

    // 모션 있으면 재생
    if ($motionVideo) {
        $motionVideo
            .one('playing', function () {
                requestAnimationFrame(function () {
                    // idleVideo 는 effectVideo 에서 처리
                    $motionVideo.removeClass('hidden');
                })
            }).get(0).play();
    }

    // 이펙트 있으면 재생
    requestAnimationFrame(function () {
        $effectVideo.removeClass('hidden');
        $idleVideo?.addClass('left-hidden').removeClass('hidden');
        if ($idleVideo) $idleVideo.get(0).currentTime = 0;
        $idleVideo?.get(0).pause();
    })
    $effectVideo.one('ended', function () {
        if ($idleVideo) {
            requestAnimationFrame(function () {
                $idleVideo.removeClass('left-hidden');
                $idleVideo.get(0).play();
                requestAnimationFrame(function () {
                    $effectVideo.addClass('hidden');
                    $motionVideo?.addClass('hidden');
                })
            })
        } else {
            $motionVideo?.addClass('hidden');
            $effectVideo.addClass('hidden');
        }
    }).get(0).play();
}

// 응답
// 상태 정보 클래스
class StatusDto {
    constructor({type, name, imageSrc, effectText, statusText, duration}) {
        this.type = type;
        this.name = name;
        this.imageSrc = imageSrc;
        this.effectText = effectText;
        this.statusText = statusText;
        this.duration = duration;
    }
}

// MoveResponse 클래스
class MoveResponse {
    constructor(data) {
        this.charOrder = data.charOrder;
        this.moveType = MoveType.byName(data.moveType);
        this.summonId = data.summonId ?? null;
        this.abilityCoolDowns = data.abilityCoolDowns || [];

        this.totalHitCount = data.totalHitCount;
        this.attackMultiHitCount = data.attackMultiHitCount;
        this.elementTypes = data.elementTypes || [];

        this.damages = data.damages || [];
        this.additionalDamages = data.additionalDamages || [];

        this.hps = data.hps || [];
        this.hpRates = data.hpRates || [];
        this.heals = data.heals || [];

        this.chargeGauges = data.chargeGauges || [];
        this.fatalChainGauge = data.fatalChainGauge ?? 0;

        // 스테이터스 매핑 [적][아군][아군][아군][아군]
        this.addedBattleStatusesList = (data.addedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s)));
        this.addedBuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
        this.addedDebuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));

        this.removedBattleStatusesList = (data.removedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s)));
        this.removedBuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'BUFF'));
        this.removedDebuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'DEBUFF'));

        this.currentBattleStatusesList = data.currentBattleStatusesList.map(statuses => statuses.filter(s => s.type !== 'PASSIVE').map(s => new StatusDto(s)));

        this.enemyAttackTargetOrders = data.enemyAttackTargetOrders ?? null;
        this.allTarget = data.allTarget ?? false;

        this.omenType = OmenType.byName(data.omenType);
        this.omenValue = data.omenValue ?? null;
        this.omenCancelCondInfo = data.omenCancelCondInfo ?? null;
        this.omenName = data.omenName ?? null;
        this.omenInfo = data.omenInfo ?? null;

        this.enemyPowerUp = data.enemyPowerUp ?? false;
        this.enemyCtMax = data.enemyCtMax ?? false;
    }
}

// JSON 배열을 MoveResponse 인스턴스 배열로 변환하는 함수
function parseMoveResponseList(jsonArray) {
    return jsonArray.map(item => new MoveResponse(item));
}

const GlobalSrc = {
    HEAL: {video: '/static/assets/video/gl/gl-heal.webm', audio: '/static/assets/audio/gl/gl-heal.mp3',},
    BEEP: {video: '', audio: '/static/assets/audio/gl/gl-beep.mp3',},
    CHARGE_ATTACK_READY: {video: '', audio: '/static/assets/audio/gl/gl-charge-attack-ready.mp3',},
    TURN_DAMAGE: {video: '', audio: '/static/assets/audio/gl/gl-turn-damage.mp3',},
    STATUS_REMOVED: {video: '', audio: '/static/assets/audio/gl/gl-status-removed.mp3',},
    SHOCKED: {video: '/static/assets/video/gl/gl-shocked.webm', audio: '/static/assets/audio/gl/gl-shocked.mp3',},
    DEBUFF: {video: '', audio: '/static/assets/audio/gl/gl-debuff.mp3'}
}
Object.freeze(GlobalSrc);


