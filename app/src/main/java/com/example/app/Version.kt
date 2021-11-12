package com.example.app

/**
 * Version Details
 *
 * @param major Major Release Version
 * @param minor Minor release version
 * @param patch patch update version
 * @param preRelease pre-release version
 * @param build build version
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: PreRelease?,
    val build: Build?,
) {
    fun print(): String = "$major.$minor.$patch${preRelease?.print() ?: ""}${build?.print() ?: ""}"
}

/**
 * Compare [Version]
 *
 * used to compare OpenGL Versions
 */
val versionComparator: Comparator<Version> = object : Comparator<Version> {
    /**
     * Compare Versions
     *
     * @param version1 minVersion
     * @param version2 currentVersion
     *
     * @return Int : <= 0 when compatible
     */
    override fun compare(version1: Version?, version2: Version?): Int {
        return when {
            version1 == null || version2 == null ->
                0
            else -> {
                if (version1.major != version2.major) {
                    return version1.major - version2.major
                }

                if (version1.minor != version2.minor) {
                    return version1.minor - version2.minor
                }

                return version1.patch - version2.patch
            }
        }
    }
}

data class PreRelease(val string: String) {
    fun print(): String = "-$string"
}

data class Build(val string: String) {
    fun print(): String = "+$string"
}

val parserBuild: Parser<Build> = parserChar('+')
    .flatMap {
        parserWhile { it != '-' && it != '+' }
            .map { Build(it) }
    }

val parserPreRelease: Parser<PreRelease> = parserChar('-')
    .flatMap {
        parserWhile { it != '-' && it != '+' }
            .map { PreRelease(it) }
    }

/**
 * Parse GLES version from device info
 */
val parserVersion: Parser<Version> =
    parserNonNegInt.flatMap { major ->
        parserChar('.').flatMap {
            parserNonNegInt.flatMap { minor ->
                parserOr(
                    parserChar('.').flatMap { parserNonNegInt },
                    parserPure(0)
                )
                    .flatMap { patch ->
                        parserPreRelease
                            .parserCatch(null)
                            .flatMap { preRelease ->
                                parserBuild
                                    .parserCatch(null)
                                    .map { build -> Pair(preRelease, build) }
                            }
                            .map { (preRelease, build) ->
                                Version(major, minor, patch, preRelease, build)
                            }
                    }
            }
        }
    }
