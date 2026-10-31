package com.remoteadb.app.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AdbManager {

    private const val ADB_KEYS_FILE = "/data/misc/adb/adb_keys"
    private const val DEFAULT_ADB_PORT = 5555

    suspend fun getAdbTcpPort(): Int = withContext(Dispatchers.IO) {
        val result = Shell.cmd("getprop service.adb.tcp.port").exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            val portStr = result.out.firstOrNull()?.trim()
            return@withContext portStr?.toIntOrNull() ?: -1
        }
        -1
    }

    suspend fun isAdbTcpEnabled(): Boolean {
        val port = getAdbTcpPort()
        return port > 0
    }

    suspend fun enableAdbTcp(port: Int = DEFAULT_ADB_PORT): Boolean = withContext(Dispatchers.IO) {
        val commands = listOf(
            "setprop service.adb.tcp.port $port",
            "stop adbd",
            "start adbd"
        )
        val result = Shell.cmd(*commands.toTypedArray()).exec()
        result.isSuccess
    }

    suspend fun disableAdbTcp(): Boolean = withContext(Dispatchers.IO) {
        val commands = listOf(
            "setprop service.adb.tcp.port -1",
            "stop adbd",
            "start adbd"
        )
        val result = Shell.cmd(*commands.toTypedArray()).exec()
        result.isSuccess
    }

    suspend fun getTrustedKeys(): List<String> = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat $ADB_KEYS_FILE").exec()
        if (result.isSuccess) {
            return@withContext result.out.filter { it.isNotBlank() }
        }
        emptyList()
    }

    suspend fun injectPublicKey(pubKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanedKey = pubKey.trim()
        if (cleanedKey.isBlank()) return@withContext false

        // Check if key already exists
        val existingKeys = getTrustedKeys()
        if (existingKeys.any { it.trim() == cleanedKey }) {
            return@withContext true
        }

        val commands = listOf(
            "mkdir -p /data/misc/adb",
            "echo \"$cleanedKey\" >> $ADB_KEYS_FILE",
            "chmod 0640 $ADB_KEYS_FILE",
            "chown system:shell $ADB_KEYS_FILE 2>/dev/null || chown 1000:2000 $ADB_KEYS_FILE 2>/dev/null || true"
        )

        val result = Shell.cmd(*commands.toTypedArray()).exec()
        result.isSuccess
    }

    suspend fun removePublicKey(pubKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanedKey = pubKey.trim()
        val existingKeys = getTrustedKeys()
        val remainingKeys = existingKeys.filter { it.trim() != cleanedKey }

        if (existingKeys.size == remainingKeys.size) {
            return@withContext true
        }

        val tempFile = "/data/local/tmp/adb_keys_temp"
        val writeCmds = mutableListOf<String>()
        writeCmds.add("rm -f $tempFile")
        for (k in remainingKeys) {
            writeCmds.add("echo \"$k\" >> $tempFile")
        }
        writeCmds.add("mv $tempFile $ADB_KEYS_FILE")
        writeCmds.add("chmod 0640 $ADB_KEYS_FILE")
        writeCmds.add("chown system:shell $ADB_KEYS_FILE 2>/dev/null || true")

        val result = Shell.cmd(*writeCmds.toTypedArray()).exec()
        result.isSuccess
    }
}
