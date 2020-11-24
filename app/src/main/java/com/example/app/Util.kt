package com.example.app

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
