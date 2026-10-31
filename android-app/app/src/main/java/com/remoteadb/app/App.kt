package com.remoteadb.app

import android.app.Application
import com.topjohnwu.superuser.Shell

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // Configure libsu for optimal root shell operation
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
        )
    }

    companion object {
        // Pre-warm root shell asynchronously
        fun warmUpShell(onResult: ((Boolean) -> Unit)? = null) {
            Shell.getShell { shell ->
                onResult?.invoke(shell.isRoot)
            }
        }
    }
}
