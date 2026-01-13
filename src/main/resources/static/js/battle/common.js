/**
 * 공통 대기 함수
 * @param ms 대기시간
 * @return {Promise<number>} 대기시간
 */
const wait = (ms) => new Promise(resolve => setTimeout(() => resolve(ms), ms));


/**
 * 데미지 요소를 만들어반환
 * 일반적으로, 해당 데미지요소는 XXDamgeWrapper 에 append 됨.
 * 난격을 포함하는 캐릭터 일반공격은 대응하지 않음.
 * @param actorOrder
 * @param elementType
 * @param moveType 행동 타입, 'attack', 'ability', chargeAttack'
 * @param damageType 데미지 타입 'NORMAL', 'ADVANTAGE', ....
 * @param index 데미지 인덱스
 * @param damage
 * @param additionalDamages
 * @param isEnemyDamage optional boolean
 * @returns {{$damage: (*|jQuery), $additionalDamage: (*|jQuery)}}
 */
function getDamageElement(actorOrder, elementType, moveType, damageType, index, damage, additionalDamages, isEnemyDamage = false) {
    let moveTypeClassName;
    switch (moveType) {
        case 'attack':
            moveTypeClassName = ' attack-damage';
            break;
        case 'ability':
            moveTypeClassName = ' ability-damage';
            break;
        case 'charge-attack':
            moveTypeClassName = ' charge-attack-damage';
            break;
        default:
            new Error('[getDamageElement] invalid type, type = ' + moveType);
    }

    let damageTypeClassname = ' ' + damageType?.toLowerCase();
    let enemyClassName = isEnemyDamage ? ' enemy' : '';
    let missClassName = damage === 'MISS' ? ' damage-miss' : '';

    // 공격
    let $damage = $(`
        <div class="damage actor-${actorOrder} element-type-${elementType.toLowerCase()} damage-index-${index} ${moveTypeClassName} ${damageTypeClassname} ${enemyClassName} ${missClassName}">
          ${damage}
        </div>
    `);

    // 추격
    let $additionalDamage = $(`
        <div class="additional-damage-wrapper actor-${actorOrder} element-type-${elementType.toLowerCase()} ${damageTypeClassname} ${missClassName} ${enemyClassName}">
          ${damage}
        </div>    
    `).append((additionalDamages || []).map(additionalDamage =>  // 추격이 존재하면 붙임
        $(`
            <div class="additional-damage element-type-${elementType.toLowerCase()} ${enemyClassName}">
              ${additionalDamage}
            </div>  
        `)
    ));

    return {$damage, $additionalDamage};
}

function openStatusInfoWrapper($statusContainer) {
    // console.log('[openStatusWrapperInfo] statusContainer = ', $statusContainer);
    let $modalBody = $('#statusInfoModal .modal-body');
    $modalBody.children().remove();

    // 적일때 추가요소
    let isEnemy = $statusContainer.is('.enemy');
    if (isEnemy) {
        let enemyActorName = gameStateManager.getState('enemyActorName');
        let enemyEstimatedAtk = gameStateManager.getState('enemyEstimatedAtk');
        let enemyEstimatedAtkString = enemyEstimatedAtk[0] ? enemyEstimatedAtk[0] + '~' + enemyEstimatedAtk[1] : '-'; // 없으면 undefined
        let $enemyStatusWrapper = $(`
            <div class="status-info-wrapper mb-2">
              <div class="actor-name"><b>${enemyActorName}</b></div>
              <div class="estimated-atk-wrapper d-flex align-items-center justify-content-center">
                <div class="actor-estimated-atk">기준 공격력: <span class="value">${enemyEstimatedAtkString}</span></div>
                <button class="btn btn-outline-warning ms-1 border-0 p-0" type="button" data-bs-toggle="collapse" data-bs-target="#estimatedAtkCollapse">
                  <i class="bi bi-question-circle"></i>
                </button>
              </div>
              <div class="collapse" id="estimatedAtkCollapse">
                기준 공격력은 현재 적의 공격력에 캐릭터의 방어력을 적용한 최소, 최대값 입니다. 그 외의 상태효과는 적용되지 않은 수치이므로 참고용으로만 사용하세요.
              </div>
            </div>
        `);
        $modalBody.append($enemyStatusWrapper);

        // 전조 효과
        let isOmenActivated = !!gameStateManager.getState('omen.type');
        if (isOmenActivated) {
            let omenInfoText = $('.omen-container .omen-info').text().replace(/\\n/g, '<br><b>특수기 효과 : </b>'); // 전조효과 : ㅁㄴㅇㄹ (\n) 특수기효과 : ㅁㄴㅇㄹ
            let omenTypeClassName = $('.omen-container .omen-text').attr('class').split(' ')[1];
            let $omenInfo = $(`
          <div class="status-info omen ${omenTypeClassName}">
            <b>전조 효과 : </b>
            ${omenInfoText} 
          </div>
        `);
            $modalBody.append($omenInfo);
        }
    }

    // 상태효과
    let $statusEffectWrapper = $(`<div class="status-effect-info-wrapper"></div>`);
    $statusContainer.find('.status').each(function (index, status) {
        let $status = $(status);
        let iconSrc = $status.find('.status-icon').attr('src');
        let statusInfoText = $status.find('.status-info-text').text();
        let statusName = $status.find('.status-name').text();
        let statusType =
            $status.data('status-type') === 'BUFF' || $status.data('status-type') === 'DISPEL_GUARD'
                ? 'status-buff'
                : 'status-debuff';
        let $statusDuration = $status.find('.status-duration');
        let remainingDuration = $statusDuration.text();
        let $statusInfo = $(`
            <div class="status-info ${statusType}">
              <div class="status-info-icon-wrapper">
                <img class="status-info-icon" src="${iconSrc}">
              </div>
              <div class="status-info-text">
                <span class="status-name fw-bold">${statusName}</span>
                <span class="status-info-duration fw-bold">[ 남은시간: ${remainingDuration} ]</span>
                <br>
                ${statusInfoText}
              </div>
            </div>
        `)
        $statusEffectWrapper.append($statusInfo);
    })
    $modalBody.append($statusEffectWrapper);
    $('.status-modal-button').click();
}

function playSe(src) {
    window.audio.play(src, {isLocal: true});
}

/**
 * 추가 사운드를 재생
 */
function playAdditionalSe(cjsName = null, motion = null, moveType = null) {
    console.debug('[playAdditionalSound] actorName = ', cjsName, ' moveType = ', moveType, ' motion = ', motion);
    if (!Sounds[cjsName]) return;
    let additionalSound = Sounds[cjsName].additional;
    if (!additionalSound) return;

    let soundByMotion = additionalSound[motion]?.src;
    let soundByMoveType = additionalSound[moveType?.name]?.src;
    let soundByMoveTypeParent = additionalSound[moveType?.getParentType()?.name]?.src;

    console.log('[playAdditionalSound] soundByMotion = ', soundByMotion, ' soundByMoveType = ', soundByMoveType, ' soundByMoveTypeParent = ', soundByMoveTypeParent);
    [soundByMotion, soundByMoveType, soundByMoveTypeParent].forEach(src => window.audio.play(src, {isLocal: true}));
}

function updateBgm(response, {stopBgm= false} = {}) {
    if (stopBgm === true) window.audio.removeBgm(window.audio.bgmSrc);
    let enemyCjses = gameStateManager.getState('enemyMainCjsNames');
    let currentEnemyCjs = enemyCjses[0];
    // moveType
    let responseMoveType = response.moveType;
    let standbyMoveType = gameStateManager.getState('omen.standbyMoveType');
    let bgmMoveType = responseMoveType === MoveType.SYNC && !!standbyMoveType ? standbyMoveType : responseMoveType; // 전조중 SYNC 에 한해, 갱신된 omen 참조
    let moveTypeBgm = Sounds[currentEnemyCjs].bgm[bgmMoveType.name];
    // hp
    let hpKey = Object.keys(Sounds[currentEnemyCjs].bgm).map(Number).sort((a, b) => a - b).find(k => k >= response.hpRates[0]);
    let hpBgm = Sounds[currentEnemyCjs].bgm[hpKey];

    let bgm = moveTypeBgm || hpBgm;
    // console.log('[updateBgm] bgm = ', bgm);
    let currentBgm = gameStateManager.getState('bgm') || 0;
    if (bgm.formOrder !== currentBgm.formOrder || bgm.index > currentBgm.index) { // 폼 체인지 or index 큰쪽
        gameStateManager.setState('bgm', bgm);
        window.audio.playBgm(bgm.src);
    }
}

/**
 *  sync 인터벌
 * @return {number} timerId
 */
function doSync() {
    if (!window.syncTimerId) { // 없을때만 등록
        requestSync();
        window.syncTimerId = window.setInterval(requestSync, 5000);
    }
}

function stopSync() {
    console.log('[stopSync] timerId = ', window.syncTimerId);
    window.clearInterval(window.syncTimerId);
    window.syncTimerId = null;
}