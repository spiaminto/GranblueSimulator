class AudioPlayer {
    constructor(audioInfos) {
        this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        this.buffers = [];
        this.audioBufferCache = new Map(); // url 과 buffer 쌍으로 이뤄진 캐시

        if (audioInfos != null && audioInfos.length > 1) {
            this.loadSounds(audioInfos).then(() => {this.buffers.length = 0}); // 캐싱
        }
    }

    async loadSound(url, delay = 0) {
        // console.log('loading sound url / delay =', url, ' / ', delay);
        if (this.audioBufferCache.has(url)) {
            this.buffers.push({ buffer: this.audioBufferCache.get(url), delay: delay });
            return;
        }
        const response = await fetch(url);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);

        this.buffers.push({ buffer: audioBuffer, delay: delay});
        this.audioBufferCache.set(url, audioBuffer);
    }

    /**
     * 여러개 동시할당 (객체리터럴)
     * @param audioInfos = [{ url: urlValue, delay: delayValue }, ...] or ['url1', 'url2', ...]
     * @returns {Promise<void>}
     */
    async loadSounds(audioInfos) {
        // console.log('audioInfos = ', audioInfos)
        if (typeof audioInfos[0] === 'string') { // 첫 원소 배열
            if (audioInfos[0].length === 1) { // 단일 url
                const loadPromise = this.loadSound(audioInfos);
                await loadPromise;
                return;
            }
            // url 문자열 배열
            const loadPromises = audioInfos.map(url => this.loadSound(url));
            await Promise.all(loadPromises);
        } else if (typeof audioInfos[0] === 'object') { // 첫원소 오브젝트 ({url, delay})
            const loadPromises = audioInfos.map(({url, delay = 0}) => this.loadSound(url, delay));
            await Promise.all(loadPromises);
        } else {
            throw new Error('[AudioPlayer.loadSounds] invalid audioInfos = ' + audioInfos);
        }
    }

    playSound(index) {
        if (index < 0 || index >= this.buffers.length) {
            console.error(`Invalid audio index: ${index}`);
            return;
        }
        const audioBuffer = this.buffers[index];
        const source = this.audioContext.createBufferSource();
        source.buffer = audioBuffer.buffer;
        source.connect(this.audioContext.destination);
        source.start(0);
    }


    playSounds(indexes) {
        indexes.forEach(indexes => this.playSound(indexes));
    }

    /**
     *  모든 사운드를 동시에 재생 (딜레이있으면 딜레이적용)
     */
    playAllSounds() {
        this.buffers.forEach((audioBuffer, index) => {
            if (audioBuffer.delay === 0) {
                this.playSound(index);
            } else {
                setTimeout(() => this.playSound(index), audioBuffer.delay);
            }
        })
    }
}

// 명시적으로 이름줘서 로드하던걸 이름 없이 url 만으로 로드하도록 위와같이 변경
// class AudioPlayer {
//     constructor() {
//         this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
//         this.buffers = new Map();
//         this.audioBufferCache = new Map();
//     }
//
//     async loadSound(name, url) {
//         console.log('loading sound name / url : ' + name + '\n' + url);
//         if (this.audioBufferCache.has(url)) {
//             this.buffers.set(name, this.audioBufferCache.get(url));
//             return;
//         }
//         const response = await fetch(url);
//         const arrayBuffer = await response.arrayBuffer();
//         const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
//         this.buffers.set(name, audioBuffer);
//         this.audioBufferCache.set(url, audioBuffer);
//     }
//
//     async loadSounds(sounds) {
//         const loadPromises = sounds.map(sound => this.loadSound(sound.name, sound.url));
//         await Promise.all(loadPromises);
//     }
//
//     playSound(name) {
//         const buffer = this.buffers.get(name);
//         if (!buffer) {
//             console.error(`No audio loaded for ${name}`);
//             return;
//         }
//         const source = this.audioContext.createBufferSource();
//         source.buffer = buffer;
//         source.connect(this.audioContext.destination);
//         source.start(0);
//     }
//
//     /**
//      *  여러개를 동시에 재생
//      * @param names '[{name : name, url : url}, {name : name, url : url}, ...]'
//      */
//     playSounds(names) {
//         names.forEach(name => this.playSound(name));
//     }
//
//     playAllSounds() {
//         for (const [name, buffer] of this.buffers) {
//             console.log('buffer = ', buffer)
//             console.log('====done')
//             const source = this.audioContext.createBufferSource();
//             source.buffer = buffer;
//             source.connect(this.audioContext.destination);
//             source.start(0);
//         }
//     }
// }

//
// class AudioPlayer {
//     constructor() {
//         this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
//         this.buffer = null;
//     }
//
//     async loadSound(url) {
//         const response = await fetch(url);
//         const arrayBuffer = await response.arrayBuffer();
//         const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);
//         this.buffer = audioBuffer;
//     }
//
//     playSound() {
//         if (!this.buffer) {
//             // console.error('No audio loaded');
//             return;
//         }
//         const source = this.audioContext.createBufferSource();
//         source.buffer = this.buffer;
//         source.connect(this.audioContext.destination);
//         source.start(0);
//         // console.log('Audio playback started');
//     }
// }