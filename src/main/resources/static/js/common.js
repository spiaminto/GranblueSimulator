$(function () {

});

/**
 * 전투중 부여된 상태효과 효과상세 모달 열기
 * @param actorIndex
 */
function openBattleStatusInfo(actorIndex) {
    console.log('[openBattleStatusInfo] actorIndex = ', actorIndex);
    actorIndex = Number(actorIndex);
    renderStatusInfoModal(gameStateManager.getState('currentStatusEffectsList')[actorIndex], {isEnemy: actorIndex === 0});
    $('.status-modal-button').click();
}

/**
 * 메타데이터 상태효과 효과상세 모달 열기
 * @param {StatusDto[]} statusDtos
 */
function openMetadataStatusInfo(statusDtos) {
    console.log('[openStatusWrapperInfo] statusDtos = ', statusDtos);
    renderStatusInfoModal(statusDtos)
    $('.status-modal-button').click();
}

/**
 * 상태효과 상세 모달 렌더링 (배틀 실시간 상태, 어빌리티 효과 상세 등)
 * @param {StatusDto[]} statusEffects
 * @param isEnemy 적 상태효과인지 확인
 */
function renderStatusInfoModal(statusEffects, {isEnemy = false} = {}) {
    let $modalBody = $('#statusInfoModal .modal-body');
    $modalBody.children().remove();

    // 적일때 추가요소
    if (isEnemy) {
        let enemyActorName = gameStateManager.getState('enemyActorName');
        let enemyEstimatedAtk = gameStateManager.getState('enemyEstimatedAtk');
        let enemyEstimatedAtkString = enemyEstimatedAtk[0] ? enemyEstimatedAtk[0] + '~' + enemyEstimatedAtk[1] : '-'; // 없으면 undefined
        let $enemyStatusWrapper = $(`
            <div class="status-info-wrapper mb-1">
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
        let omen = gameStateManager.getState('omen');
        if (!omen.isEmpty()) {
            let $omenInfo = $(`
              <div class="status-info omen ${omen.type.className}">
                <span class="status-name fw-bold">전조</span>
                <br>
                ${omen.info}
                <br>
                <span class="status-name fw-bold">특수기: ${omen.name}</span>
                <br>
                ${omen.chargeAttackInfo}
              </div>
            `);
            $modalBody.append($omenInfo);
        }
    }

    // 상태효과
    let $statusEffectWrapper = createStatusWrapperElement(statusEffects);
    $modalBody.append($statusEffectWrapper);
}


/**
 * 상태효과 상세 요소 만들어 반환
 */
function createStatusWrapperElement(statusEffects, {metadata = false} = {}) {
    let $statusEffectWrapper = $(`<div class="status-effect-info-wrapper"></div>`);
    if (!metadata) statusEffects = statusEffects.filter(statusEffect => statusEffect.type === 'BUFF' || statusEffect.type === 'DEBUFF');
    statusEffects.forEach(effect => { // StatusDto
        let statusType = effect.type && 'status-' + effect.type.toLowerCase();
        let durationPrefix = '효과시간';

        let duration = effect.remainingDuration;
        duration = duration === 0 ? '즉시발동' : duration;


        // 메타데이터 보기시 추가
        // if (metadata) {
        //     durationPrefix = '효과시간';
        //
        //     duration = effect.duration; // 메타데이터
        //     duration = duration > 999 ? '영속'
        //         : duration === 0 ? '즉시발동'
        //             : duration === 180 ? '180초'
        //                 : duration + '턴';
        // }

        let $statusInfo = $(`
            <div class="status-info ${statusType}">
              <div class="status-info-icon-wrapper">
                <img class="status-info-icon" src="${effect.iconSrc}">
              </div>
              <div class="status-info-text">
                <span class="status-name fw-bold">${effect.name}</span>
                <span class="status-info-duration fw-bold">[ ${durationPrefix}: ${duration} ]</span>
                <br>
                ${infoToRenderText(effect.statusText)}
              </div>
            </div>
        `);
        $statusEffectWrapper.append($statusInfo);
    });

    return $statusEffectWrapper;
}

function infoToRenderText(info) {
    let renderText = '';
    if (info.indexOf('◆') > 0) {
        info.split('◆').forEach((item, index) => {
            if (index === 0) {
                renderText = renderText + item;
                return;
            }
            renderText += `<br><span class="text-lightblue">${'◆' + item}</span>`;
        })
    } else {
        renderText += `<span>${info}</span>`;
    }
    return renderText;
}

// XSS 이스케이프용
const HTML_CHAR_MAP = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
    '/': '&#x2F;'
};

/**
 * XSS 방지를 위한 HTML 이스케이프 전역 함수
 */
function escapeHtml(str) {
    if (str == null) return ''; // null, undefined
    return String(str).replace(/[&<>"'/]/g, (s) => HTML_CHAR_MAP[s]);
}