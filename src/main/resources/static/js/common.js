$(function () {

});

/**
 * 커맨드 상세 모달 열기
 * @param moveInfo
 */
function openCommandMetadataInfoModal(moveInfo) {
    //헤더
    let commandName = '';
    if (moveInfo.type === 'ABILITY') {
        let classname =
            moveInfo.abilityType === 'ATTACK' ? 'text-red'
                : moveInfo.abilityType === 'BUFF' ? 'text-yellow'
                    : moveInfo.abilityType === 'DEBUFF' ? 'text-blue'
                        : moveInfo.abilityType === 'HEAL' ? 'text-green'
                            : '';
        commandName = `<span class="${classname}">${moveInfo.displayAbilityType} </span>어빌리티`;
    } else if (moveInfo.type === 'CHARGE_ATTACK') {
        commandName = '오의';
    }
    let $modalHeader = $(`
                    <div class="modal-header">
                      <h4 class="modal-title">${commandName} 정보</h4>
                    </div>
                `);

    //바디
    let $modalBody = $(`<div class="modal-body"></div>`);

    // 상태효과
    let $statusEffectWrapper = createStatusWrapperElement(moveInfo.statusEffects, {metadata: true});
    $modalBody.append($statusEffectWrapper);

    // 커맨드 정보
    let moveName = moveInfo.type === 'CHARGE_ATTACK' ? '오의: ' + moveInfo.name : moveInfo.name;
    let $commandInfoWrapper = $(`
                 <div class="ability-info-wrapper">
                   <div class="ability-info-icon-wrapper">
                     <img class="ability-info-icon" src="${moveInfo.iconImageSrc}">
                   </div>
                   <div class="ability-info-text-wrapper">
                     <div class="ability-info-name">${moveName}</div>
                     <div class="ability-info-text">
                       ${TooltipParser.parse(moveInfo.info)}
                     </div>
                   </div>
                 </div>`);
    // 데미지
    if (moveInfo.damageRate > 0) {
        $commandInfoWrapper.find('.ability-info-text').prepend($(`
                <div class="damage-info fw-semibold">
                  <i class="bi bi-claude text-pink"></i> 데미지: ${moveInfo.damageRate.toFixed(1)}배
                  <span>${moveInfo.hitCount > 0 ? ` X ${moveInfo.hitCount}회` : ''}</span>
                </div>
            `));
    }
    // 쿨다운
    let cooldown = moveInfo.maxCooldown; // number, -1: 재사용불가인듯
    let cooldownString = cooldown >= 0 && cooldown < 999 ? cooldown + ' 턴' : '재사용 불가';
    if (moveInfo.type === 'ABILITY' || moveInfo.type === 'SUMMON') {
        $commandInfoWrapper.find('.ability-info-text-wrapper').append($(`
            <div class="ability-info-text cooldown">
              <i class="bi bi-clock-fill text-dark-yellow"></i> 쿨타임: ${cooldownString} 
            </div>`));
    }
    $modalBody.append($commandInfoWrapper);

    //돔추가
    let $modalContent = $('#commandMetadataInfoModal .modal-content');
    $modalContent.find('.modal-header').replaceWith($modalHeader);
    $modalContent.find('.modal-body').empty().append($modalBody.children());
}

/**
 * 전투중 부여된 상태효과 효과상세 모달 열기
 * @param actorIndex
 */
function openBattleStatusInfo(actorIndex) {
    // console.log('[openBattleStatusInfo] actorIndex = ', actorIndex);
    actorIndex = Number(actorIndex);
    const isEnemy = actorIndex === 0;
    let statusEffects = gameStateManager.getState('currentStatusEffectsList')[actorIndex];

    let $modalBody = $('#statusInfoModal .modal-body');
    $modalBody.children().remove();

    // 적일때 추가요소
    let $omenInfo = $();
    if (isEnemy) {
        let enemyActorName = gameStateManager.getState('enemyActorName');

        let enemyEstimatedAtk = gameStateManager.getState('enemyEstimatedAtk');
        let enemyEstimatedAtkString = '';
        if (enemyEstimatedAtk[0] === 0 || enemyEstimatedAtk[0] > 0) enemyEstimatedAtkString = enemyEstimatedAtkString + enemyEstimatedAtk[0].toLocaleString();
        if (enemyEstimatedAtk[1] === 0 || enemyEstimatedAtk[1] > 0) enemyEstimatedAtkString = enemyEstimatedAtkString + ' ~ ' + enemyEstimatedAtk[1].toLocaleString();
        if (!enemyEstimatedAtkString) enemyEstimatedAtkString = '-';

        let $enemyStatusWrapper = $(`
            <div class="status-info-wrapper">
              <div class="actor-name">${enemyActorName}</div>
              <div class="estimated-atk-wrapper d-flex align-items-center justify-content-center">
                <div class="actor-estimated-atk">공격 1회 당 예상 데미지: <span class="value fw-bold">${enemyEstimatedAtkString}</span></div>
                <button class="btn btn-outline-warning ms-1 border-0 p-0" type="button" data-bs-toggle="collapse" data-bs-target="#estimatedAtkCollapse">
                  <i class="bi bi-question-circle"></i>
                </button>
              </div>
              <div class="collapse" id="estimatedAtkCollapse">
                예상데미지 중 최솟값, 최댓값을 표시합니다.<br>
                <span class="fw-semibold">배율 증가 효과는 반영하지 않습니다.</span><br> 
                랜덤, 복수속성 공격시 캐릭터와 동일속성으로 계산됩니다.
              </div>
            </div>
        `);
        $modalBody.append($enemyStatusWrapper);

        // 전조 효과
        let omen = gameStateManager.getState('omen');
        let cancelConditionString = omen.cancelConditions.map(condition => condition.type === 'IMPOSSIBLE' ? '해제불가' : condition.info + ' ' + condition.remainValue.toLocaleString()).join(' / ');
        if (!omen.isEmpty()) {
            $omenInfo = $(`
              <div class="status-info omen ${omen.type.className}">
                <div class="status-name">전조 <span class="fw-bold">${omen.name}</span> 발생중</div>
                <div><span class="text-green"><i class="bi bi-check2-circle me-1 icon-bold"></i>해제조건: </span>${cancelConditionString}</div>
                <div>${omen.info}</div>
                <div class="d-flex">
                  <div style="width: 19%; white-space: nowrap;">
                    <span class="fw-semibold text-red"><i class="bi bi-x-circle-fill me-1"></i></i>해제실패: </span>
                  </div>
                  <div>
                    ${TooltipParser.parse(omen.chargeAttackInfo, {isStatus: true})}
                  </div>
                </div>
              </div>
            `);
        }
    }

    // 더블, 트리플어택 확률
    let doubleAttackRate = gameStateManager.getState('doubleAttackRates')[actorIndex];
    let doubleAttackRateNumber = Number(doubleAttackRate);
    if (doubleAttackRateNumber) {
        doubleAttackRate = doubleAttackRateNumber >= 999 ? '확정' : doubleAttackRateNumber < 0 ? "불가" : doubleAttackRateNumber + '%';
    }
    let tripleAttackRate = gameStateManager.getState('tripleAttackRates')[actorIndex];
    let tripleAttackRateNumber = Number(tripleAttackRate);
    if (tripleAttackRateNumber) {
        tripleAttackRate = tripleAttackRateNumber >= 999 ? '확정' : tripleAttackRateNumber < 0 ? "불가" : tripleAttackRateNumber + '%';
    }
    let $multipleAttackRateWrapper = $(`
        <div class="status-info-wrapper multi-attack-rate-wrapper mb-1 d-flex justify-content-center gap-2">
          <div style="font-size: 10.5px; font-weight: 300" class="d-inline-block"><img style="width: 16px" src="/static/gbf/img/status/status_1004.png"> 더블어택 확률: <span class="fw-semibold">${doubleAttackRate}</span></div>
          <div style="font-size: 10.5px; font-weight: 300" class="d-inline-block"><img style="width: 16px" src="/static/gbf/img/status/status_1044.png"> 트리플어택 확률: <span class="fw-semibold">${tripleAttackRate}</span></div>
        </div>
    `);

    // 상태효과
    let $statusEffectWrapper = createStatusWrapperElement(statusEffects, {metadata: false});
    $statusEffectWrapper.prepend($omenInfo); // 전조 있으면 끼워넣기
    $statusEffectWrapper.prepend($multipleAttackRateWrapper); //연속공격확률 끼워넣기
    $modalBody.append($statusEffectWrapper);

    $('#statusInfoModal .modal-header').toggle(!isEnemy); // 적일경우 숨김

    $omenInfo.length > 0 && applyGlow($omenInfo, {color: getComputedStyle($omenInfo[0]).borderTopColor});

    $('.status-modal-button').click();
}

/**
 * 메타데이터 상태효과 효과상세 모달 열기
 * @param {StatusDto[]} statusDtos
 */
function openMetadataStatusInfo(statusDtos) {
    // console.log('[openStatusWrapperInfo] statusDtos = ', statusDtos);

    let $modalBody = $('#statusInfoModal .modal-body');
    $modalBody.children().remove();

    let $statusEffectWrapper = createStatusWrapperElement(statusDtos, {metadata: true});
    $modalBody.append($statusEffectWrapper);

    $('.status-modal-button').click();
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

        // 최대 레벨 또는 효과량
        let maxLevelText = '';
        if (effect.maxLevel > 0) {
            maxLevelText = effect.durationType === 'LEVEL_INFINITE' ? '최대' + effect.maxLevel + '회' : '최대Lv' + effect.maxLevel;

            // 누적 처리
            if (effect.name.includes('누적')) {
                if (metadata) maxLevelText = '최대 ' + effect.maxLevel + '회';
                else {
                    maxLevelText = '현재 ' + effect.level + '회';
                }
            }

            // 특수 케이스 처리
            let uniqueCase = ['활성『알파』', '활성『베타』', '활성『감마』'];
            if (uniqueCase.includes(effect.name)) {
                maxLevelText += '0';
            }

            maxLevelText = `(${maxLevelText})`
        }

        // 삭제불가
        let removableText = effect.removable ? '' : '-삭제불가';

        // 효과시간
        let durationType = effect.durationType;
        let duration = effect.remainingDuration;
        let durationText =
            durationType.includes('INFINITE') ? '영속'
                : durationType.includes('TURN') ? duration + '턴'
                    : durationType.includes('TIME') ? Math.floor(duration / 60) + ':' + (duration % 60).toString().padStart(2, '0') + '분' : '오류';

        if (duration === 0) {
            durationPrefix = '';
            durationText = '즉효';
        }

        // 요소 생성
        let $statusInfo = $(`
            <div class="status-info ${statusType}">
              <div class="status-info-icon-wrapper">
                <img class="status-info-icon" src="${effect.iconSrc}">
              </div>
              <div class="status-info-text">
                <div class="status-name">
                  ${effect.name} 
                  <span class="status-info-duration fw-bold" style="margin-left: 2px">
                    <i class="bi bi-hourglass-split text-dark-yellow"></i>
                    ${durationText}
                    <span class="fw-normal text-dark-red removable-info">${removableText}</span>
                  </span>
                </div>
                ${TooltipParser.parse(effect.statusText, {isStatus: true})}
                <span style="font-size: 0.95em" class="fw-semibold">${maxLevelText}</span>
              </div>
            </div>
        `);
        $statusEffectWrapper.append($statusInfo);

        // 디버그용
        if (effect.modifiers) {
            let $modifierWrapper = $(`
            <div> ----- </div>
            <div class="status-modifier-wrapper">
              ${Object.entries(effect.modifiers).map(([modifierName, modifierValue]) => modifierName + ": " + modifierValue).join('<br>')}
            </div>
        `);
            $statusInfo.find('.status-info-text').append($modifierWrapper);
        }

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
            renderText += `<br><span class="text-light-pink">${'◆' + item}</span>`;
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

/**
 * glow 효과 적용
 * @param {HTMLElement|jQuery} element - 글로우를 적용할 요소
 * @param {Object} [options={}] - 옵션
 * @param {string} [options.color='#ffffff'] - 글로우 색상 (hex, rgb, rgba 모두 가능)
 * @param {number} [options.duration=2] - duration
 * @param {number} [options.spread=4] - spread 값
 */
function applyGlow(element, {color = '#ffffff', duration = 1.5, spread = 4, blur = 6} = {}) {
    if (!element) {
        console.warn('[applyGlow] element not found');
        return;
    }
    const el = $(element)[0];
    el.style.setProperty('--glow-color', color);
    el.style.setProperty('--glow-spread', `${spread}px`);
    el.style.setProperty('--glow-blur', `${blur}px`);
    el.style.animationDuration = `${duration}s`;
    el.classList.add('glowing');
}

function removeGlow(element) {
    $(element)[0].classList.remove('glowing');
}


// 파서 등록
window.TooltipParser = (function () {
    // const newlineRegex = /\n|\\n|\[\/\]/g; // \n, \\n, [\]
    const newlineRegex = /\[\/\]/g; // [/]
    const spaceRegex = /\[;+\]/g; // [;], [;;], [;;;], ...

    const wrapTagMap = {
        // 두께
        '볼드': (content) => `<span class="fw-bold">${content}</span>`,
        '세미볼드': (content) => `<span class="fw-semibold">${content}</span>`,
        '라이트': (content) => `<span class="fw-light">${content}</span>`,

        // 컬러 태그
        '블루': (content) => `<span class="text-blue fw-semibold">${content}</span>`,
        '시안': (content) => `<span class="text-cyan fw-semibold">${content}</span>`,
        '그린': (content) => `<span class="text-green fw-semibold">${content}</span>`,
        '레드': (content) => `<span class="text-red fw-semibold">${content}</span>`,
        '옐로': (content) => `<span class="text-yellow fw-semibold">${content}</span>`,
        '핑크': (content) => `<span class="text-pink fw-semibold">${content}</span>`,
        '라이트블루': (content) => `<span class="text-light-blue fw-semibold">${content}</span>`,
        '라이트그린': (content) => `<span class="text-light-green fw-semibold">${content}</span>`,
        '라이트퍼플': (content) => `<span class="text-light-purple fw-semibold">${content}</span>`,
        '퍼플': (content) => `<span class="text-purple fw-semibold">${content}</span>`,
        '브라운': (content) => `<span class="text-brown fw-semibold">${content}</span>`,
        '네이비': (content) => `<span class="text-navy fw-semibold">${content}</span>`,
        '다크옐로우': (content) => `<span class="text-dark-yellow fw-semibold">${content}</span>`,
        '다크블루': (content) => `<span class="text-dark-blue fw-semibold">${content}</span>`,
        '다크레드': (content) => `<span class="text-dark-red fw-semibold">${content}</span>`,
        '다크그린': (content) => `<span class="text-dark-green fw-semibold">${content}</span>`,

        // 특수기 태그
        '데미지': (content) => `<div class="damage-info fw-semibold"><i class="bi bi-claude text-pink"></i> 데미지: ${content}</div>`,
        '적데미지': (content) => `<span class="fw-semibold">${content}</span>`,
        '영창기': (content) => `<span class="omen incant-attack inline">${content}</span>`,
        'CT기': (content) => `<span class="omen charge-attack inline">${content}</span>`,
        'HP트리거': (content) => `<span class="omen hp-trigger inline">${content}</span>`,
    };
    const wrapTagKeys = Object.keys(wrapTagMap).join('|');
    const wrapTagRegex = new RegExp(`\\[(${wrapTagKeys})\\](.*?)\\[\\/\\1\\]`, 'gs');

    // 상태효과
    const statusIconRegex = /\[상태콘=([^\]]+)\]/g; // 특수 (지정되어 내려옴)
    const effectIconMap = { // 범용 아이콘
        // '공격행동 횟수증가': '6107',
        // '공격행동 횟수 증가': '6107',
        '쿨타임을 초기화': '1058',
        '쿨타임 초기화': '1058',
        '쿨타임초기화': '1058',
    };
    const sortedEffectIconKeywords = Object.keys(effectIconMap).sort((a, b) => b.length - a.length);
    const effectIconRegex = new RegExp(`(${sortedEffectIconKeywords.map(k => k.replace(/ /g, '\\s')).join('|')})`, 'g');

    const keywordMap = {
        '적에게': 'text-red fw-semibold',
        '적의': 'text-red fw-semibold',

        '자신을 제외한 아군': 'text-blue fw-semibold',
        '자신 이외의 아군': 'text-blue fw-semibold',
        '아군 전체': 'text-blue fw-semibold',
        '아군전체': 'text-blue fw-semibold',
        '아군에게': 'text-blue fw-semibold',
        '아군의': 'text-blue fw-semibold',
        '아군': 'text-blue fw-semibold',

        '다음에 배치된 캐릭터': 'text-blue fw-semibold',

        '참전자 전체': 'text-cyan fw-semibold',
        '참전자전체': 'text-cyan fw-semibold',
        '참전자': 'text-cyan fw-semibold',

        '주인공에게': 'text-light-purple fw-semibold',
        '주인공이': 'text-light-purple fw-semibold',
        '주인공의': 'text-light-purple fw-semibold',
        '주인공': 'text-light-purple fw-semibold',

        '자신에게': 'text-light-green fw-semibold',
        '자신이': 'text-light-green fw-semibold',
        '자신의': 'text-light-green fw-semibold',
        '자신': 'text-light-green fw-semibold',

        // '효과' 관련 키워드 (선택 사항)
        '-삭제불가': 'text-dark-red fw-semibold',
        '삭제 불가': 'text-dark-red fw-semibold',
        '삭제불가': 'text-dark-red fw-semibold',
        '-필중효과 제외': 'text-dark-red fw-semibold',
        '-필중효과제외': 'text-dark-red fw-semibold',
        '-필중': 'text-dark-red fw-semibold',
        '필중': 'text-dark-red fw-semibold',

        '일반공격 수행': 'text-light-pink',
        '일반공격 후': 'text-light-pink',
        '일반공격시': 'text-light-pink',
        '일반공격후': 'text-light-pink',

        '자동발동': 'text-yellow',
        '자동 발동': 'text-yellow',
        '횟수 2배': 'text-yellow',
        '전체화': 'text-yellow',
    };
    const sortedKeywords = Object.keys(keywordMap).sort((a, b) => b.length - a.length); // '적에게'가 '적'보다 먼저 매칭되도록 길이를 기준으로 내림차순 정렬
    const keywordHighlightRegex = new RegExp(`(${sortedKeywords.join('|')})`, 'g');

    const parseCache = {}; // 캐시

    return {
        parse(plainText, {isStatus = false} = {}) {
            if (!plainText) return '';
            if (parseCache[plainText]) return parseCache[plainText];

            let htmlText = plainText;

            // 열고 닫는 태그 - 컬러, 특수기, ui구분
            htmlText = htmlText.replace(wrapTagRegex, (match, tagName, content) => {
                return wrapTagMap[tagName]?.(content) ?? match;
            });

            // 단일태그 - 상태아이콘
            htmlText = htmlText.replace(statusIconRegex, (_, iconId) => {
                return `<img class="status-info-icon-inline" src="/static/gbf/img/status/status_${iconId}.png">`;
            });

            // 공통효과 아이콘 처리
            htmlText = htmlText.replace(effectIconRegex, (match) => {
                const iconName = effectIconMap[match];
                if (!iconName) return match;
                const appendIconClassname = isStatus ? 'small' : '';
                return `<img class="status-info-icon-inline ${appendIconClassname}" src="/static/gbf/img/status/status_${iconName}.png">${match}`;
            });

            // 키워드 하이라이팅
            htmlText = htmlText.replace(keywordHighlightRegex, (match) => {
                return `<span class="${keywordMap[match]}">${match}</span>`;
            });

            // 줄바꿈 처리
            htmlText = htmlText.replace(newlineRegex, '<br>');

            // 공백 처리
            htmlText = htmlText.replace(spaceRegex, (match) => {
                const spaceCount = match.length - 2;
                return `<span style="display:inline-block;width:${spaceCount}em"></span>`;
            });

            // 추가정보 처리
            let renderText = '';
            if (htmlText.indexOf('◆') > 0) {
                htmlText.split('◆').forEach((item, index) => {
                    if (index === 0) {
                        renderText += item;
                        return;
                    }
                    renderText += `<span class="additional-info text-light-blue fw-light">${'◆ ' + item}</span>`;
                });
            } else {
                renderText = htmlText;
            }

            parseCache[plainText] = renderText;
            return renderText;
        },

        clearCache() {
            for (let key in parseCache) delete parseCache[key];
        }
    };
})();