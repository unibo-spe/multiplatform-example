import java.net.URI

object NodeVersions {
    private const val NODE_DIST_URL = "https://nodejs.org/dist"

    // only release directories (e.g. "v24.1.0/"), not pre-releases (e.g. "v27.0.0-alpha.0.0.0/")
    private val RELEASE_DIR_REGEX = "\"v(\\d+\\.\\d+\\.\\d+)/\"".toRegex()

    private val VERSIONS: Set<StableVersion> by lazy {
        RELEASE_DIR_REGEX.findAll(URI(NODE_DIST_URL).toURL().readText())
            .map { StableVersion.parse(it.groupValues[1]) }
            .toSet()
    }

    private val VERSIONS_CACHE = mutableMapOf<String, String>()

    private val MAJOR_REGEX = "^(\\d+)(?:\\.[a-zA-Z])?$".toRegex()
    private val MAJOR_MINOR_REGEX = "^(\\d+)\\.(\\d+)(?:\\.[a-zA-Z])?$".toRegex()
    private val FULL_VERSION_REGEX = "^(\\d+)\\.(\\d+)\\.(\\d+)$".toRegex()
    private val LATEST_VERSION_REGEX = "^v?(\\d+)-latest|latest-v?(\\d+)$".toRegex(RegexOption.IGNORE_CASE)

    @Suppress("NAME_SHADOWING")
    private fun findLatestVersion(version: String): StableVersion? {
        val version = version.trim()
        if (version.equals("latest", ignoreCase = true)) {
            return VERSIONS.max()
        }
        val match = MAJOR_REGEX.matchEntire(version)
            ?: LATEST_VERSION_REGEX.matchEntire(version)
            ?: MAJOR_MINOR_REGEX.matchEntire(version)
            ?: FULL_VERSION_REGEX.matchEntire(version)
            ?: return null
        val numbers = match.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
        val minVersion = StableVersion.of(numbers)
        val upperBound = minVersion.nextMajor()
        return VERSIONS.filter { it >= minVersion && it < upperBound }.maxOrNull()
    }

    fun latest(version: String = "latest"): String =
        VERSIONS_CACHE.computeIfAbsent(version) {
            findLatestVersion(it)?.toVersionString() ?: error("No such node version: $version")
        }
}
