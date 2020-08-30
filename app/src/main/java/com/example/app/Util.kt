package com.example.app

import android.content.res.Configuration
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

data class PermissionResultEvent(val requestCode: Int, val grantResults: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PermissionResultEvent

        if (requestCode != other.requestCode) return false
        if (!grantResults.contentEquals(other.grantResults)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestCode
        result = 31 * result + grantResults.contentHashCode()
        return result
    }
}

interface ResumeBehavior {
    val resumeBehavior: BehaviorSubject<Boolean>
}

interface RequestPermissionResultEvents {
    val requestPermissionResultEvents: PublishSubject<PermissionResultEvent>
}

interface ConfigurationChangedEvents {
    val configurationChangedEvents: PublishSubject<Configuration>
}
