/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.benchmark

import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

internal object WarningState {
    private const val TAG = "Benchmark"

    val WARNING_PREFIX: String
    private var warningString: String? = null

    fun acquireWarningStringForLogging(): String? {
        val ret = warningString
        warningString = null
        return ret
    }

    private val isEmulator = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
            "google_sdk" == Build.PRODUCT

    private val isDeviceRooted =
        arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        ).any { File(it).exists() }

    private fun isCpuLocked(): Boolean {
        // If any core is detected to have a variable clock speed, this flag is set to false.
        var cpuClocksAreLocked = true
        val cpuDir = File("/sys/devices/system/cpu")
        val coreDirs = cpuDir.list { current, name ->
            File(
                current,
                name
            ).isDirectory && name.matches("^cpu[0-9]+".toRegex())
        }.map { "${cpuDir.path}/$it" }

        // Check cpu clock locking by testing against each core's minimum possible clock speed.
        for (coreDir in coreDirs) {
            try {
                // skip disabled cores
                if (readFileTextOrNull("$coreDir/online") == "0") {
                    break
                }

                val cpuMinFreqStr = readFileTextOrNull(
                    "$coreDir/cpufreq/scaling_min_freq"
                ) ?: break
                val cpuMinFreq = Integer.parseInt(cpuMinFreqStr)

                val cpuAllAvailFreqStr = readFileTextOrNull(
                    "$coreDir/cpufreq/scaling_available_frequencies"
                ) ?: break
                val cpuAvailMinFreq =
                    cpuAllAvailFreqStr.split("\\s+".toRegex()).map { Integer.parseInt(it) }.min()

                val coreMightBeLocked = cpuAvailMinFreq != cpuMinFreq
                cpuClocksAreLocked = cpuClocksAreLocked && coreMightBeLocked
            } catch (e: Exception) {
                // Failed to read the cpu clock speed! This can happen in a number of cases where
                // the required files are either missing due to running on an emulator or when the
                // files have been tampered with / not generated by the OS for some cores.
                if (!isEmulator) {
                    Log.d(TAG, "Error while reading cpu clock state", e)
                }
            }
        }
        return cpuClocksAreLocked
    }

    init {
        val appInfo = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationInfo
        var warningPrefix = ""
        var warningString = ""
        if (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            warningPrefix += "DEBUGGABLE_"
            warningString += """
                |WARNING: Debuggable Benchmark
                |    Benchmark is running with debuggable=true, which drastically reduces
                |    runtime performance in order to support debugging features. Run
                |    benchmarks with debuggable=false. Debuggable affects execution speed
                |    in ways that mean benchmark improvements might not carry over to a
                |    real user's experience (or even regress release performance).
            """.trimMarginWrapNewlines()
        }
        if (isEmulator) {
            warningPrefix += "EMULATOR_"
            warningString += """
                |WARNING: Running on Emulator
                |    Benchmark is running on an emulator, which is not representative of
                |    real user devices. Use a physical device to benchmark. Emulator
                |    benchmark improvements might not carry over to a real user's
                |    experience (or even regress real device performance).
            """.trimMarginWrapNewlines()
        }
        if (Build.FINGERPRINT.contains(":eng/")) {
            warningPrefix += "ENG-BUILD_"
            warningString += """
                |WARNING: Running on Eng Build
                |    Benchmark is running on device flashed with a '-eng' build. Eng builds
                |    of the platform drastically reduce performance to enable testing
                |    changes quickly. For this reason they should not be used for
                |    benchmarking. Use a '-user' or '-userdebug' system image.
            """.trimMarginWrapNewlines()
        }

        if (isDeviceRooted && !isCpuLocked()) {
            warningPrefix += "UNLOCKED_"
            warningString += """
                |WARNING: Unstable CPU clocks
                |    Benchmark appears to be running on a rooted device with unlocked CPU
                |    clocks. Unlocked CPU clocks can lead to inconsistent results due to
                |    dynamic frequency scaling, and thermal throttling. On a rooted
                |    device, lock your device clocks to a stable frequency with lockClocks.sh
            """.trimMarginWrapNewlines()
        }

        WARNING_PREFIX = warningPrefix
        if (!warningString.isEmpty()) {
            this.warningString = warningString
            warningString.split("\n").map { Log.w(TAG, it) }
        }
    }

    /**
     * Same as trimMargins, but add newlines on either side.
     */
    private fun String.trimMarginWrapNewlines(): String {
        return "\n" + trimMargin() + " \n"
    }

    /**
     * Read the text of a file as a String, null if file doesn't exist.
     */
    private fun readFileTextOrNull(path: String): String? {
        File(path).run {
            return if (exists()) readText().trim() else null
        }
    }
}