package com.pixelboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Manages the two child processes:
 *  1. LiDAR driver — command depends on selected [LidarModel]:
 *       LD19  → `ros2 launch ldlidar_stl_ros2 ld19.launch.py`
 *       A1_M8 → `ros2 launch sllidar_ros2 sllidar_a1_launch.py`
 *  2. ROS bridge   — `python3 ros_bridge.py`  (emits JSON to stdout)
 *
 * Both processes are launched with `bash -c "source setup.bash && <cmd>"`
 * so that ROS2 is available on PATH.
 */
class ProcessManager(private val projectRoot: String) {

    private var driverProcess: Process? = null
    private var bridgeProcess: Process? = null

    private val setupBash = "$projectRoot/rw/install/setup.bash"

    // ── Driver ────────────────────────────────────────────────────────────────

    fun startDriver(model: LidarModel) {
        stopDriver()
        val launchCmd = when (model) {
            LidarModel.LD19  -> "ros2 launch ldlidar_stl_ros2 ld19.launch.py"
            LidarModel.A1_M8 -> "ros2 launch sllidar_ros2 sllidar_a1_launch.py"
        }
        val cmd = listOf("bash", "-c", "source $setupBash && $launchCmd")
        driverProcess = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .directory(File(projectRoot))
            .start()
    }

    fun stopDriver() {
        driverProcess?.destroy()
        driverProcess = null
        // Kill stale nodes for either driver
        runCatching {
            ProcessBuilder("bash", "-c",
                "pkill -f 'ldlidar_stl_ros2_node|sllidar_node' 2>/dev/null || true")
                .start().waitFor()
        }
    }

    val isDriverAlive: Boolean
        get() = driverProcess?.isAlive == true

    // ── Bridge ────────────────────────────────────────────────────────────────

    /**
     * Starts ros_bridge.py and returns a Flow that emits each JSON line.
     * The flow completes when the process exits or is killed.
     */
    fun streamBridgeOutput(): Flow<String> = flow {
        stopBridge()
        val cmd = listOf(
            "bash", "-c",
            "source $setupBash && python3 $projectRoot/ros_bridge.py"
        )
        val proc = ProcessBuilder(cmd)
            .directory(File(projectRoot))
            .start()
        bridgeProcess = proc

        try {
            proc.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.isNotBlank()) emit(line)
                }
            }
        } finally {
            proc.destroy()
        }
    }.flowOn(Dispatchers.IO)

    fun stopBridge() {
        bridgeProcess?.destroy()
        bridgeProcess = null
        runCatching {
            ProcessBuilder("bash", "-c", "pkill -f ros_bridge.py 2>/dev/null || true")
                .start().waitFor()
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun stopAll() {
        stopBridge()
        stopDriver()
    }
}
