import { onMounted, onUnmounted, ref } from 'vue'

// 重启会取消上一轮请求，旧请求结束后不得覆盖新一轮状态。
export function usePoll(task: (signal: AbortSignal) => Promise<boolean>, limitMs = 120000) {
    const exhausted = ref(false)
    const running = ref(false)
    let timer: ReturnType<typeof setTimeout> | undefined
    let controller: AbortController | undefined
    let started = 0
    let attempt = 0
    let disposed = false
    let generation = 0
    async function tick(version: number) {
        if (disposed || version !== generation) return
        if (Date.now() - started > limitMs) {
            exhausted.value = true
            return
        }
        if (document.hidden) {
            timer = setTimeout(() => tick(version), 3000)
            return
        }
        const active = new AbortController()
        controller = active
        running.value = true
        let again = true
        try {
            again = await task(active.signal)
        } catch {
            /* 页面展示本次错误，下轮继续恢复。 */
        } finally {
            if (version === generation) running.value = false
        }
        if (again && !disposed && version === generation)
            timer = setTimeout(() => tick(version), Math.min(2000 + attempt++ * 1000, 10000))
    }
    function stop() {
        generation++
        clearTimeout(timer)
        controller?.abort()
        running.value = false
    }
    function restart() {
        if (disposed) return
        stop()
        started = Date.now()
        attempt = 0
        exhausted.value = false
        void tick(generation)
    }
    onMounted(restart)
    onUnmounted(() => {
        disposed = true
        stop()
    })
    return { exhausted, running, restart, stop }
}
