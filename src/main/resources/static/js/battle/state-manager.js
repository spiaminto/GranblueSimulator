function createStateManager(initialState = {}) {
    const state = initialState;

    const subscribers = new Map(); // key: path, value: Set callback
    initRenderSubscribers();
    initOnSetSubscribers();

    /**
     * 상태 구독
     * @param path :String stage.gGameStatus 이하 path
     * @param callback
     * @return {(function(): void)|*} unsubscribe()
     */
    function subscribe(path, callback) {
        if (!subscribers.has(path)) {
            subscribers.set(path, new Set());
        }
        subscribers.get(path).add(callback);

        return () => {
            subscribers.get(path).delete(callback);
            if (subscribers.get(path).size === 0) {
                subscribers.delete(path); // path 없으면 삭제
            }
        };
    }

    /**
     * 상태 변경
     * @param {string} path stage.gGameStatus 이하 path
     * @param value 변경할 값
     * @param {Object} [options] 옵션
     * @param {boolean} [options.force] 같은 값 강제 업데이트 여부
     */
    function setState(path, value, options = {}) {
        const pathKeys = path.split(".");
        let target = state; // {... target: { lastKey: value }}

        for (let i = 0; i < pathKeys.length - 1; i++) {
            let key = pathKeys[i];
            if (!(key in target) || typeof target[key] !== "object" || target[key] === null) {
                // throw new Error(`[setState] key "${keys[i]}" does not exist.`); // 해당 필드 없으면 그냥 에러
                target[key] = {}; // 없으면 초기화 후 아래서 대입
            }
            target = target[key];
        }

        const lastKey = pathKeys[pathKeys.length - 1];
        const prevValue = target[lastKey];
        if (!options.force && _.isEqual(prevValue, value)) return; // 변경 없으면 실행 안함

        target[lastKey] = value;

        notifySubscribers(pathKeys, value, prevValue);
    }

    function notifySubscribers(pathKeys, value, prevValue) {
        subscribers.forEach((callbacks, subscribedPath) => {
            const subscribedPathKeys = subscribedPath.split('.');
            // 길이와 패턴이 정확히 일치하는지 확인 (abilities.* 구독중이면 abilities.5104 감지, abilities.5104.cooldown 은 무시)
            if (subscribedPathKeys.length !== pathKeys.length) return;

            let isMatch = true;
            const wildcardValues = []; // 실제 들어온 path.XXX 로 변경할 key array
            for (let i = 0; i < subscribedPathKeys.length; i++) {
                if (subscribedPathKeys[i] === '*') {
                    wildcardValues.push(pathKeys[i]);
                } else if (subscribedPathKeys[i] !== pathKeys[i]) {
                    isMatch = false;
                    break;
                }
            }
            if (!isMatch) return; // path 다르면 즉시 종료

            // 콜백 실행
            const params = wildcardValues.length > 0
                ? [...wildcardValues, value, prevValue] // wildCard 있을경우 첫번째 파라미터로 넘김
                : [value, prevValue];

            callbacks.forEach(callback => {
                try {
                    callback(...params);
                } catch (e) {
                    console.error("[setState] path = ", pathKeys.join('.'), " value = ", value, " Subscriber error: ", e);
                }
            });
        });
    }

    /**
     * 상태 조회
     * @param path :String stage.gGameStatus 이하 path
     * @return {{}|*}
     */
    function getState(path) {
        if (!path) return state;
        const keys = path.split(".");
        return keys.reduce((acc, key) => (acc ? acc[key] : undefined), state);
    }

    // renderer
    function initRenderSubscribers() {
        //battleCanvas
        // hp bar
        subscribe('hps', renderHp);
        subscribe('hpRates', renderHpRate);
        subscribe('enemyTriggerHps', renderEnemyTriggerHps);
        // guard
        subscribe('guardStates', renderGuards);
        // omen
        subscribe('omen', renderOmen);
        // indicator
        subscribe('indicator.moveName', renderMoveNameIndicator);
        subscribe('indicator.moveResultHonor', renderMoveResultHonorIndicator);
        subscribe('currentTurn', renderTurnIndicator);
        subscribe('remainingTimeString', renderRemainingTimeIndicator);
        // attackButton
        subscribe('isQuestCleared', renderAttackButton);
        subscribe('isQuestFailed', renderAttackButton);
        subscribe('isAttackClicked', renderAttackButton);

        //commandContainer
        // barrier, chargeGauge, fatalChainGauge
        subscribe('barriers', renderBarriers);
        subscribe('chargeGauges', renderChargeGauge);
        subscribe('enemyMaxChargeGauge', renderEnemyMaxChargeGauge);
        subscribe('fatalChainGauge', renderFatalChainGauge);
        // ability
        subscribe('ability', renderAllAbilities);
        subscribe('ability', renderAllAbilityIndicators);
        subscribe('ability.*', renderSingleAbility)
        // ability details
        subscribe('abilityCoolDowns', renderAbilityCoolDowns); // usableIndicator 통합
        subscribe('abilitySealeds', renderAbilityCoolDowns);
        // summon
        subscribe('summon', renderAllSummons);
        // summon details
        subscribe('leaderActorId', renderSummonButton);
        subscribe('summonCooldowns', renderSummonCooldowns);
        subscribe('usedSummon', renderSummonCooldowns);
        subscribe('unionSummonInfo', renderUnionSummonChance);
        // status
        subscribe('currentStatusEffectsList', renderCurrentStatusEffectsIcons);

        //memberInfoContainer
        subscribe('memberInfos', renderMemberInfoContainer)

        //chat
        subscribe('chatMessages', renderChatMessages);

        //modal
        // potion
        subscribe("potion.counts", renderPotionCount);
    }

    // onSet
    function initOnSetSubscribers() {
        // subscribe('omen', onOmenSet);
    }

    return {subscribe, setState, getState};
}


async function initGameStatus() {
    // 로컬 스토리지 초기화
    if (localStorage.getItem('abilityStatusEffectInfoCheck') === null) localStorage.setItem('abilityStatusEffectInfoCheck', 'true');
    if (localStorage.getItem('abilityInfoCheck') === null) localStorage.setItem('abilityInfoCheck', 'true');
    if (localStorage.getItem('abilityInfoCheck') === 'true') {
        $('#showAbilityInfoCheck').prop('checked', true).trigger('change');
    }

    let roomId = $('#roomInfo').data('room-id');
    let initData = await fetch(`/api/rooms/${roomId}/members/me/battle-init`)
        .then(response => response.json())
        .catch(error => console.error('[initGameStatus] fetch error:', error));
    window.assetInfos = initData.assetInfos;

    let characterInfos = initData.characterInfo;
    let enemyInfo = initData.enemyInfo;
    let fatalChainGauge = initData.fatalChainGauge;
    let fatalChainInfo = initData.fatalChainInfo;
    let leaderActorId = initData.leaderActorId; // nullable
    let summonInfos = initData.summonInfos;
    let triggerHps = initData.triggerHps;

    window.stage = {}
    stage.gGameStatus = {}
    stage.gGameStatus.raid_union_summon_name = 'hello';
    stage.gGameStatus.isQuestCleared = false;
    stage.gGameStatus.isAttackClicked = false;

    // 그랑블루 사양에 따른 일부 변수추가할당
    stage.global = {};
    stage.global.is_pair_chain = false; // 보이스 재생용인듯. 잇는캐릭 잇고 없는 캐릭 잇어서 비활성화
    stage.pJsnData = {}; // quest_clear.js

    // 프론트에서만 임시로 관리되는 상태
    stage.gGameStatus.doUnionSummon = true; // 합체소환 여부
    stage.gGameStatus.usedSummon = initData.usedSummon; // 초기 로드시만 서버갱신
    stage.gGameStatus.currentTurn = initData.currentTurn;
    stage.gGameStatus.startTime = new Date(initData.startTime); // createManager 후 초당 인터벌 갱신 하나달아줌

    // 기본 상태
    stage.gGameStatus.leaderActorId = leaderActorId;
    stage.gGameStatus.actorIds = [initData.enemyInfo.id, null, null, null, null];

    // 어빌리티, 소환석, 서포트어빌리티, 오의, 페이탈체인 메타데이터 등록
    stage.gGameStatus.ability = {};
    stage.gGameStatus.supportAbility = {1:[], 2:[], 3:[], 4:[]}; // 서포트 어빌리티는 불변 및 메타데이터 고정, 요청하지 않는 읽기전용
    stage.gGameStatus.summon = {};
    stage.gGameStatus.chargeAttack = {};
    let allMoves = [
        ...Object.values(characterInfos).flatMap(characterInfo => characterInfo.abilities),
        ...Object.values(characterInfos).flatMap(characterInfo => characterInfo.supportAbilities),
        ...Object.values(characterInfos).map(characterInfo => characterInfo.chargeAttack),
        fatalChainInfo,
        ...summonInfos,
    ]
    Object.entries(characterInfos).forEach(([currentOrder, characterInfo]) => {
        stage.gGameStatus.actorIds[Number(currentOrder)] = characterInfo.id;

        allMoves.forEach((move, index) => {
            let moveInfo =
                new MoveInfo({
                    type: move.type,
                    id: move.id,
                    name: move.name,
                    order: move.order,
                    actorId: move.actorId,
                    actorIndex: move.actorIndex,
                    info: move.info,
                    cooldown: move.cooldown,
                    maxCooldown: move.maxCooldown,
                    iconImageSrc: move.iconImageSrc,
                    cutinImageSrc: move.cutinImageSrc,
                    additionalType: move.abilityType,
                    statusEffects: move.statusEffects,
                    portraitImageSrc: move.portraitImageSrc,
                });
            switch (move.type) {
                case 'ABILITY':
                    stage.gGameStatus.ability[move.id] = moveInfo;
                    // stage.gGameStatus.abilityByActor[move.actorIndex][index] = moveInfo;
                    break;
                case 'SUPPORT_ABILITY':
                    stage.gGameStatus.supportAbility[move.actorIndex][move.order - 1] = moveInfo;
                    // stage.gGameStatus.abilityByActor[move.actorIndex][index] = moveInfo;
                    break;
                case 'CHARGE_ATTACK':
                    stage.gGameStatus.chargeAttack[move.id] = moveInfo;
                    break;
                case 'SUMMON':
                    stage.gGameStatus.summon[move.id] = moveInfo;
                    break;
                case 'FATAL_CHAIN':
                    stage.gGameStatus.fatalChain = moveInfo;
                    stage.gGameStatus.ability[move.id] = moveInfo;
                    break;
            }
        });

    });
    // 공격
    stage.gGameStatus.attack = new MoveInfo({
        type: 'ATTACK',
        name: '공격',
        iconImageSrc: '/static/assets/img/ui/ui-attack-icon.png'
    });
    // 포션
    stage.gGameStatus.potion = {allPotion: {}, potion: {}, elixir: {}};
    stage.gGameStatus.potion.single = new MoveInfo({
        type: 'POTION',
        additionalType: 'single',
        iconImageSrc: '/static/assets/img/ui/potion.jpg',
        //actorId 가 나중에 타겟으로 들어감
    });
    stage.gGameStatus.potion.all = new MoveInfo({
        type: 'POTION',
        additionalType: 'all',
        iconImageSrc: '/static/assets/img/ui/all-potion.jpg',
    });
    stage.gGameStatus.potion.elixir = new MoveInfo({
        type: 'POTION',
        additionalType: 'elixir',
        iconImageSrc: '/static/assets/img/ui/elixir.jpg',
    });
    let potionCounts = $('#potionModal .potion-icon-container .count').map((index, element) => element.textContent).toArray();
    stage.gGameStatus.potion.counts = potionCounts;

    // 특수 상태
    stage.gGameStatus.enemyActorName = enemyInfo.name; // 첫 로드, 폼체인지 시 갱신
    stage.gGameStatus.enemyFormOrder = enemyInfo.formOrder;
    stage.gGameStatus.isFatalDamaged = [false, false, false, false, false]; // 대 데미지 피격 / 피격으로 인한 빈사상태 발생시 true

    // 인디케이터
    stage.gGameStatus.indicator = {}
    stage.gGameStatus.indicator.moveName = '';
    stage.gGameStatus.indicator.moveResultHonor = 0;

    // 채팅
    stage.gGameStatus.lastChatId = null;
    stage.gGameStatus.chatMessages = [];

    // syncResponse 로 초기화
    stage.gGameStatus.barriers = [0, 0, 0, 0, 0];
    stage.gGameStatus.abilityCoolDowns = [];
    stage.gGameStatus.abilityUsables = [];
    stage.gGameStatus.currentStatusEffectsList = {};
    stage.gGameStatus.enemyMaxChargeGauge = 0;
    stage.gGameStatus.omen = OmenDto.empty();
    stage.gGameStatus.guardStates = [];
    stage.gGameStatus.summonCooldowns = []; // 편의를 위해 별도로 저장
    stage.gGameStatus.unionSummonInfo = null; // 합체소환시 이 값을 프론트에서 그대로 쓰기 위해 상태 갱신시 한꺼번에 하지 말것. SYNC 에서만 갱신할것.
    stage.gGameStatus.canChargeAttacks = [false, false, false, false, false];

    // gameStateManager 생성 & response 사용 시작 =========================================================================
    window.gameStateManager = createStateManager(stage.gGameStatus);

    // SYNC 요청 및 응답 반환
    let responses = await requestSync(true);
    let syncResponses = parseMoveResponseList(responses);
    let syncResponse = syncResponses[0];
    window.stage.processing = {};
    window.stage.processing.response = syncResponse;
    console.log('[initGameStatus] syncResponse = ', syncResponse);

    // 첫 로드로 렌더링이 튀는걸 방지하기 위해 게이지 관련 요소들은 SSR 로 렌더링됨
    stage.gGameStatus.hps = syncResponse.hps;
    stage.gGameStatus.hpRates = syncResponse.hpRates;
    gameStateManager.setState('enemyTriggerHps', JSON.parse($('.enemy-info-container').attr('data-trigger-hps'))); // hpRate 상태 필요
    stage.gGameStatus.fatalChainGauge = syncResponse.fatalChainGauge;

    // 요소 초기 렌더링 (어빌리티, 소환석)
    gameStateManager.setState('ability', gameStateManager.getState('ability'), {force: true});
    gameStateManager.setState('summon', gameStateManager.getState('summon'), {force: true});

    //전조, 차지턴
    gameStateManager.setState('enemyMaxChargeGauge', syncResponse.enemyMaxChargeGauge);
    gameStateManager.setState('chargeGauges', syncResponse.chargeGauges, {force: true}); // 적의 차지턴은 렌더링 해줘야됨
    gameStateManager.setState('omen', syncResponse.omen);

    // 가드
    let initialGuardStates = $('#actorContainer .guard-status').toArray().map(element => element.dataset.initialGuardState === 'true');
    initialGuardStates.unshift(null); // 적 null
    setTimeout(() => gameStateManager.setState('guardStates', initialGuardStates), 1000); // 살짝 딜레이
    
    // 주인공 및 주인공 종속 상태
    let isLeaderAlive = !!gameStateManager.getState('actorIds').find(actorId => actorId === leaderActorId);
    if (!isLeaderAlive) {
        gameStateManager.setState('leaderActorId', null);
    } else {
        // 리더 사망시 아래는 렌더링 하지 않음
        gameStateManager.setState('summonCooldowns', syncResponse.summonCooldowns);
        gameStateManager.setState('unionSummonInfo', syncResponse.unionSummonInfo);
    }

    // 기타 상태
    gameStateManager.setState('barriers', syncResponse.barriers);
    gameStateManager.setState('canChargeAttacks', syncResponse.canChargeAttacks);
    gameStateManager.setState('currentStatusEffectsList', syncResponse.currentStatusEffectsList);
    gameStateManager.setState('abilitySealeds', syncResponse.abilitySealeds); // 첫 로드는 쿨다운 보다 먼저해야 usableIndicator 에서 제대로 렌더링 가능
    gameStateManager.setState('abilityCoolDowns', syncResponse.abilityCoolDowns);

    gameStateManager.setState('enemyEstimatedAtk', syncResponse.enemyEstimatedAtk);

    // 멤버정보 로드
    requestMembersInfo();
    // 채팅 로드
    stage.gGameStatus.chatMessages = null; // 렌더링 하지 않고 미리 초기화 (init 구분)
    requestChat();

    // 시간 갱신용 인터벌
    const battleDuration = 30; // (m), 30분 고정
    window.startTimeIntervalId = window.setInterval(() => {
        let startTime = gameStateManager.getState('startTime'); // Date
        const elapsedMs = Date.now() - startTime; // (ms)
        const remainingMs = (battleDuration * 60 * 1000) - elapsedMs; // (ms)
        if (remainingMs <= 0) {
            // TODO 전투 종료 처리
            return;
        }

        const remainingSeconds = Math.ceil(remainingMs / 1000);
        const minutePart = Math.floor(remainingSeconds / 60);
        const secondPart = remainingSeconds % 60;
        let formattedRemainingTime = `${minutePart.toString().padStart(2, '0')}:${secondPart.toString().padStart(2, '0')}`;
        gameStateManager.setState('remainingTimeString', formattedRemainingTime);
    }, 1000);

    // 나머지 참전자 무브 잇는경우 처리 (아마 이펙트 겹칠것)
    if (syncResponses.length > 1) {
        processResponseMoves(syncResponses.slice(1));
    }
}