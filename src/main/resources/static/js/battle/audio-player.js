class AudioPlayer {
    constructor() {
        this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        this.buffers = [];
    }

    async loadSound(url, delay = 0) {
        const response = await fetch(url);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);

        this.buffers.push({ buffer: audioBuffer, delay: delay});
    }

    /**
     * 여러개 동시할당 (객체리터럴)
     * @param audioInfos = [{ url: urlValue, delay: delayValue(ms) }, ...] or ['url1', 'url2', ...]
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
        // console.log('loadSounds audioPlayer.buffers = ', this.buffers);
    }

    playSound(index) {
        // console.log('play sound index = ', index);
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

    /**
     *  모든 사운드를 동시에 재생 (딜레이있으면 딜레이적용)
     */
    playAllSounds() {
        // console.log('play all sounds', this.buffers);
        this.buffers.forEach((audioBuffer, index) => {
            if (audioBuffer.delay === 0) {
                this.playSound(index);
            } else {
                setTimeout(() => this.playSound(index), audioBuffer.delay);
            }
        })
    }
}
