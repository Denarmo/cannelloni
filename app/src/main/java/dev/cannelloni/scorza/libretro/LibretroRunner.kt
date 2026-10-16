package dev.cannelloni.scorza.libretro

import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class SystemAvInfo(
    val width: Int,
    val height: Int,
    val fps: Double,
    val sampleRate: Int
)

// Posts a block to the GL thread (e.g. GLSurfaceView.queueEvent). Returns
// false if it could not be posted (no view), in which case it never runs.
fun interface GlDispatcher {
    fun post(block: Runnable): Boolean
}

class LibretroRunner {

    companion object {
        init {
            System.loadLibrary("retro_bridge")
        }

        const val PIXEL_FORMAT_RGB565 = 2
        const val PIXEL_FORMAT_XRGB8888 = 1
        const val DEVICE_NONE = 0
        const val DEVICE_JOYPAD = 1
        const val MAX_PORTS = 4

        // PSP state saves can take hundreds of ms; leave plenty of headroom.
        private const val GL_DISPATCH_TIMEOUT_MS = 5_000L
    }

    // HW-rendered cores (SET_HW_RENDER) must only be driven from the GL thread
    // that owns their context. The renderer records that thread here; the
    // activity installs glDispatcher so state ops called from other threads
    // are marshalled there. Software cores ignore both.
    @Volatile var glThread: Thread? = null
    @Volatile var glDispatcher: GlDispatcher? = null

    private fun needsGlDispatch(): Boolean =
        glDispatcher != null && Thread.currentThread() !== glThread && nativeIsHwRender()

    // Run block on the GL thread and wait for it. On timeout the queued block is
    // cancelled (if it has not started) and either run here (runDirectOnTimeout,
    // for teardown when the GL thread may be gone) or default is returned.
    private fun <T> onGlThread(default: T, runDirectOnTimeout: Boolean = false, block: () -> T): T {
        val dispatcher = glDispatcher
        if (dispatcher == null || !needsGlDispatch()) return block()
        val claimed = AtomicBoolean(false)
        val done = CountDownLatch(1)
        var result: T = default
        val posted = dispatcher.post {
            if (claimed.compareAndSet(false, true)) {
                try { result = block() } finally { done.countDown() }
            }
        }
        if (!posted) return if (runDirectOnTimeout) block() else default
        if (done.await(GL_DISPATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return result
        if (claimed.compareAndSet(false, true)) {
            return if (runDirectOnTimeout) block() else default
        }
        // Already running on the GL thread; let it finish.
        done.await()
        return result
    }

    fun loadCore(corePath: String): Boolean = nativeLoadCore(corePath)

    fun init(systemDir: String, saveDir: String) = nativeInit(systemDir, saveDir)

    fun initAudio(sampleRate: Int, contentFps: Double) = nativeAudioInit(sampleRate, contentFps)
    fun getAudioDiagnostics(): String = nativeAudioGetDiagnostics()
    fun stopAudio() = nativeAudioStop()
    fun setAudioMuted(muted: Boolean) = nativeAudioSetMuted(muted)
    fun pauseAudio() = nativeAudioPause()
    fun resumeAudio() = nativeAudioResume()

    fun loadGame(romPath: String): SystemAvInfo? {
        val result = nativeLoadGame(romPath) ?: return null
        return SystemAvInfo(
            width = result[0],
            height = result[1],
            fps = result[2] / 1_000_000.0,
            sampleRate = result[3]
        )
    }

    fun run() = nativeRun()

    // --- HW rendering (GLES cores such as PPSSPP) ---

    /** True once the core requested an OpenGL ES context (SET_HW_RENDER) during loadGame. */
    fun isHwRender(): Boolean = nativeIsHwRender()

    /** GL thread: create the FBO and call the core's context_reset. Idempotent per EGL context. */
    fun hwContextReset(): Boolean = nativeHwContextReset()

    /** GL thread: call the core's context_destroy and free the FBO. No-op if not alive. */
    fun hwContextDestroy() = nativeHwContextDestroy()

    fun isHwContextAlive(): Boolean = nativeHwContextAlive()

    data class HwTarget(val texture: Int, val width: Int, val height: Int, val bottomLeftOrigin: Boolean)

    /** GL thread: the FBO colour texture the core renders into, or null before hwContextReset. */
    fun getHwTarget(): HwTarget? {
        val a = nativeHwGetTarget()
        if (a[0] == 0) return null
        return HwTarget(a[0], a[1], a[2], a[3] != 0)
    }

    /** Bumped whenever the core reports new geometry (SET_GEOMETRY / SET_SYSTEM_AV_INFO). */
    fun getGeometrySerial(): Int = nativeGetGeometrySerial()

    /** Current A/V info, including updates the core sent after loadGame. */
    fun getCurrentAvInfo(): SystemAvInfo? {
        val result = nativeGetCurrentAvInfo() ?: return null
        return SystemAvInfo(
            width = result[0],
            height = result[1],
            fps = result[2] / 1_000_000.0,
            sampleRate = result[3]
        )
    }

    fun setInput(port: Int, mask: Int) = nativeSetInput(port, mask)
    fun setAnalog(port: Int, index: Int, x: Int, y: Int) = nativeSetAnalog(port, index, x, y)
    fun setPointer(x: Int, y: Int, pressed: Boolean) = nativeSetPointer(x, y, pressed)

    fun setControllerPortDevice(port: Int, device: Int) = nativeSetControllerPortDevice(port, device)

    data class ControllerType(val desc: String, val id: Int)

    fun getControllerTypes(port: Int): List<ControllerType> {
        val arr = nativeGetControllerTypes(port)
        val result = mutableListOf<ControllerType>()
        var i = 0
        while (i + 1 < arr.size) {
            result.add(ControllerType(arr[i], arr[i + 1].toIntOrNull() ?: 0))
            i += 2
        }
        return result
    }

    fun getRotation(): Int = nativeGetRotation()
    fun getPixelFormat(): Int = nativeGetPixelFormat()
    fun getFrameWidth(): Int = nativeGetFrameWidth()
    fun getFrameHeight(): Int = nativeGetFrameHeight()
    fun hasNewFrame(): Boolean = nativeHasNewFrame()
    // HW cores read the FBO back with glReadPixels, which needs the GL thread.
    fun copyFrame(buffer: ByteBuffer) = onGlThread(Unit) { nativeCopyFrame(buffer) }
    /** False if nothing was copied (no frame yet, or HW core without a current GL context). */
    fun copyLastFrame(buffer: ByteBuffer): Boolean = onGlThread(false) { nativeCopyLastFrame(buffer) }

    fun saveState(path: String): Boolean = onGlThread(false) { nativeSaveState(path) }
    fun loadState(path: String): Boolean = onGlThread(false) { nativeLoadState(path) }
    fun saveSRAM(path: String): Boolean = nativeSaveSRAM(path)
    fun loadSRAM(path: String): Boolean = nativeLoadSRAM(path)

    fun applyEmuCheats(codes: List<String>) = nativeApplyEmuCheats(codes.toTypedArray())
    fun setRetroCheats(table: LongArray) = nativeSetRetroCheats(table)
    fun cheatMemorySize(): Long = nativeCheatMemorySize()

    // unloadGame calls context_destroy first for HW cores, so teardown order is automatic.
    fun unloadGame() = onGlThread(Unit, runDirectOnTimeout = true) { nativeUnloadGame() }
    fun deinit() = onGlThread(Unit, runDirectOnTimeout = true) { nativeDeinit() }
    fun reset() = onGlThread(Unit) { nativeReset() }

    fun getSystemInfo(): Pair<String, String> {
        val arr = nativeGetSystemInfo()
        return (arr[0] ?: "") to (arr[1] ?: "")
    }

    fun getAspectRatio(): Float = nativeGetAspectRatio()

    data class CoreOptionValue(val value: String, val label: String)
    data class CoreOption(
        val key: String,
        val desc: String,
        val values: List<CoreOptionValue>,
        val selected: String,
        val category: String,
        val info: String
    )
    data class CoreOptionCategory(val key: String, val desc: String, val info: String)

    fun getCoreOptions(): List<CoreOption> {
        val arr = nativeGetCoreOptions()
        val result = mutableListOf<CoreOption>()
        var i = 0
        while (i + 6 < arr.size) {
            val rawValues = arr[i + 2].split('|').filter { it.isNotEmpty() }
            val rawLabels = arr[i + 5].split('|')
            val values = rawValues.mapIndexed { idx, v ->
                CoreOptionValue(v, rawLabels.getOrElse(idx) { v })
            }
            result.add(CoreOption(
                key = arr[i],
                desc = arr[i + 1],
                values = values,
                selected = arr[i + 3],
                category = arr[i + 4],
                info = arr[i + 6]
            ))
            i += 7
        }
        return result
    }

    fun getCoreCategories(): List<CoreOptionCategory> {
        val arr = nativeGetCoreCategories()
        val result = mutableListOf<CoreOptionCategory>()
        var i = 0
        while (i + 2 < arr.size) {
            result.add(CoreOptionCategory(arr[i], arr[i + 1], arr[i + 2]))
            i += 3
        }
        return result
    }

    fun setCoreOption(key: String, value: String) = nativeSetCoreOption(key, value)

    fun hasDiskControl(): Boolean = nativeHasDiskControl()
    fun getDiskCount(): Int = nativeGetDiskCount()
    fun getDiskIndex(): Int = nativeGetDiskIndex()
    fun setDiskIndex(index: Int): Boolean = nativeSetDiskIndex(index)
    fun getDiskLabel(index: Int): String? = nativeGetDiskLabel(index)

    fun getCoreLogs(): List<String> = nativeGetCoreLogs().toList()

    fun getMemoryDescriptors(): List<String> = nativeGetMemoryDescriptors().toList()

    private external fun nativeLoadCore(corePath: String): Boolean
    private external fun nativeInit(systemDir: String, saveDir: String)
    private external fun nativeAudioInit(sampleRate: Int, contentFps: Double)
    private external fun nativeAudioGetDiagnostics(): String
    private external fun nativeAudioStop()
    private external fun nativeAudioSetMuted(muted: Boolean)
    private external fun nativeAudioPause()
    private external fun nativeAudioResume()
    private external fun nativeLoadGame(romPath: String): IntArray?
    private external fun nativeRun()
    private external fun nativeIsHwRender(): Boolean
    private external fun nativeHwContextReset(): Boolean
    private external fun nativeHwContextDestroy()
    private external fun nativeHwContextAlive(): Boolean
    private external fun nativeHwGetTarget(): IntArray
    private external fun nativeGetGeometrySerial(): Int
    private external fun nativeGetCurrentAvInfo(): IntArray?
    private external fun nativeSetInput(port: Int, mask: Int)
    private external fun nativeSetAnalog(port: Int, index: Int, x: Int, y: Int)
    private external fun nativeSetPointer(x: Int, y: Int, pressed: Boolean)
    private external fun nativeSetControllerPortDevice(port: Int, device: Int)
    private external fun nativeGetControllerTypes(port: Int): Array<String>
    private external fun nativeGetRotation(): Int
    private external fun nativeGetPixelFormat(): Int
    private external fun nativeGetFrameWidth(): Int
    private external fun nativeGetFrameHeight(): Int
    private external fun nativeHasNewFrame(): Boolean
    private external fun nativeCopyFrame(buffer: ByteBuffer)
    private external fun nativeCopyLastFrame(buffer: ByteBuffer): Boolean
    private external fun nativeSaveState(path: String): Boolean
    private external fun nativeLoadState(path: String): Boolean
    private external fun nativeSaveSRAM(path: String): Boolean
    private external fun nativeLoadSRAM(path: String): Boolean
    private external fun nativeApplyEmuCheats(codes: Array<String>)
    private external fun nativeSetRetroCheats(table: LongArray)
    private external fun nativeCheatMemorySize(): Long
    private external fun nativeUnloadGame()
    private external fun nativeDeinit()
    private external fun nativeReset()
    private external fun nativeGetSystemInfo(): Array<String>
    private external fun nativeGetAspectRatio(): Float
    private external fun nativeGetCoreOptions(): Array<String>
    private external fun nativeGetCoreCategories(): Array<String>
    private external fun nativeSetCoreOption(key: String, value: String)
    private external fun nativeHasDiskControl(): Boolean
    private external fun nativeGetDiskCount(): Int
    private external fun nativeGetDiskIndex(): Int
    private external fun nativeSetDiskIndex(index: Int): Boolean
    private external fun nativeGetDiskLabel(index: Int): String?
    private external fun nativeGetCoreLogs(): Array<String>
    private external fun nativeGetMemoryDescriptors(): Array<String>
}
