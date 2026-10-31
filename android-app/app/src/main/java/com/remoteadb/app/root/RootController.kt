package com.remoteadb.app.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class RootProvider {
    MAGISK,
    KERNEL_SU,
    APATCH,
    SUPER_SU,
    UNKNOWN,
    NONE
}

data class RootStatus(
    val isRooted: Boolean,
    val provider: RootProvider,
    val versionInfo: String = ""
)

object RootController {

    suspend fun checkRoot(): RootStatus = withContext(Dispatchers.IO) {
        val isRoot = Shell.isAppGrantedRoot() == true
        if (!isRoot) {
            return@withContext RootStatus(isRooted = false, provider = RootProvider.NONE)
        }

        val provider = detectRootProvider()
        RootStatus(
            isRooted = true,
            provider = provider.first,
            versionInfo = provider.second
        )
    }

    private fun detectRootProvider(): Pair<RootProvider, String> {
        val magiskCheck = Shell.cmd("magisk -v").exec()
        if (magiskCheck.isSuccess && magiskCheck.out.isNotEmpty()) {
            return Pair(RootProvider.MAGISK, "Magisk ${magiskCheck.out.firstOrNull() ?: ""}")
        }

        val ksuCheck = Shell.cmd("ksud -V").exec()
        if (ksuCheck.isSuccess && ksuCheck.out.isNotEmpty()) {
            return Pair(RootProvider.KERNEL_SU, "KernelSU ${ksuCheck.out.firstOrNull() ?: ""}")
        }

        val apatchCheck = Shell.cmd("apd -V").exec()
        if (apatchCheck.isSuccess && apatchCheck.out.isNotEmpty()) {
            return Pair(RootProvider.APATCH, "APatch ${apatchCheck.out.firstOrNull() ?: ""}")
        }

        val suCheck = Shell.cmd("su -v").exec()
        if (suCheck.isSuccess && suCheck.out.isNotEmpty()) {
            val v = suCheck.out.firstOrNull() ?: ""
            return if (v.contains("SuperSU", ignoreCase = true)) {
                Pair(RootProvider.SUPER_SU, v)
            } else {
                Pair(RootProvider.UNKNOWN, v)
            }
        }

        return Pair(RootProvider.UNKNOWN, "Generic SU")
    }
}
