override suspend fun streamItems(
    remoteApi: WebDav,
    recursive: Boolean,
    filter: ((NetworkFile) -> Boolean)?,
    batchSize: Int,
    onBatch: suspend (List<NetworkFile>) -> Unit
): Result<Long> {

    val rootPath = normalizeDirectoryPath(remoteApi.path.ifBlank { "/" })
    val MAX_TOTAL = 300
    var emitted = 0L

    suspend fun emitOne(file: WebDavFile): Boolean {
        if (emitted >= MAX_TOTAL) return false
        val nf = NetworkFile(
            remoteApi,
            normalizePath(file.path),
            file.name,
            file.isDirectory,
            file.contentLength,
            com.alpha.showcase.common.utils.getFileExtension(file.name),
            file.lastModified.ifBlank { file.creationDate }
        )
        if (filter?.invoke(nf) == false) return true
        onBatch(listOf(nf))
        emitted++
        return true
    }

    return try {
        val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
        val baseUrl = urlWithoutPath.ifBlank { remoteApi.url }
        webDavClient = WebDavClient(baseUrl, remoteApi.user, RConfig.decrypt(remoteApi.passwd))

        if (!recursive) {
            webDavClient.listFiles(rootPath)
                .filter { !it.isDirectory }
                .take(MAX_TOTAL)
                .forEach { emitOne(it) }
            return Result.success(emitted)
        }

        val pendingDirs = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        pendingDirs.add(rootPath)

        while (pendingDirs.isNotEmpty()) {
            if (emitted >= MAX_TOTAL) break
            val currentPath = normalizeDirectoryPath(pendingDirs.removeLast())
            if (!visited.add(currentPath)) continue

            val resources = webDavClient.listFiles(currentPath)
            for (res in resources) {
                if (emitted >= MAX_TOTAL) break
                val normalized = normalizePath(res.path)
                if (isSamePath(normalized, currentPath)) continue

                if (res.isDirectory) {
                    pendingDirs.add(normalizeDirectoryPath(normalized))
                } else {
                    if (!emitOne(res)) break
                }
            }
        }

        Result.success(emitted)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
