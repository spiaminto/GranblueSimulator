class AudioPlayer {
    constructor() {
        this.audioContext = new (window.AudioContext || window.webkitAudioContext)();
        this.buffers = [];
    }

    async loadSound(src) {
        const response = await fetch(src);
        const arrayBuffer = await response.arrayBuffer();
        const audioBuffer = await this.audioContext.decodeAudioData(arrayBuffer);

        this.buffers.push(audioBuffer);
    }

    async loadSounds(audioSrcs) {
        console.log('[AudioPlayer.loadSounds] audioSrcs = ', audioSrcs);
        if (audioSrcs.length > 0) {
            const loadPromises = audioSrcs.map(src => this.loadSound(src));
            await Promise.all(loadPromises);
        } else {
            console.log('[AudioPlayer.loadSounds] invalid audioSrcs = ', audioSrcs);
        }
    }

    playSound(index) {
        // console.log('play sound index = ', index);
        if (index < 0 || index >= this.buffers.length) {
            console.error(`[AudioPlayer.playSound] Invalid audio index: ${index}`);
            return;
        }
        const audioBuffer = this.buffers[index];
        if (!audioBuffer) {
            console.error(`[AudioPlayer.playSound] Invalid audio Buffer: ${audioBuffer}`)
        }
        const source = this.audioContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(this.audioContext.destination);
        source.start(0);
    }

    /**
     *  모든 사운드를 동시에 재생
     */
    playAllSounds() {
        // console.log('[AudioPlayer.playAllSounds] play all sounds', this.buffers);
        this.buffers.forEach((audioBuffer, index) => {
            this.playSound(index);
        })
    }
}
