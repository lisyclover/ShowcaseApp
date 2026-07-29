package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.WebDavClient
import com.alpha.showcase.common.networkfile.WebDavFile
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.RConfig
import io.ktor.http.Url
import io.ktor.http.fullPath

class NativeWebdavSourceRepo :
    SourceRepository<WebDav, NetworkFile>,
    FileDirSource<WebDav, NetworkFile>,
    BatchSourceRepository<WebDav, NetworkFile> {

    private lateinit var webDavClient: WebDavClient

    override suspend fun getItem(remoteApi: WebDav): Result<NetworkFile> {
        TODO("Not yet implemented")
    }

    override suspend fun getFileDirItems(remoteApi: WebDav): Result<List<NetworkFile>> {
        return try {
            val urlWithoutPath = remoteApi.url.replace(Url(remoteApi.url).fullPath, "")
            val baseUrl = urlWithoutPath.ifBlank { remoteApi.url }
            webDavClient = WebDavClient(baseUrl, remoteApi.user, RConfig.decrypt(remoteApi.passwd))
            val path = remoteApi.path.ifBlank { "/" }
            val contents = webDavClient.listFiles(path)
            val resultList = contents.map { file ->
                NetworkFile(
                    remoteApi,
                    normalizePath(file.path),
                    file.name,
                    file.isDirectory,
                    file.contentLength,
                    file.name.getExtension(),
                    file.lastModified.ifBlank { file.creationDate }
                )
            }
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getItems(
        remoteApi: WebDav,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?
    ): Result<List<NetworkFile>> {
        val files = mutableListOf<NetworkFile>()
        val streamResult = streamItems(remoteApi, recursive, filter, 100) { batch ->
            files.addAll(batch)
        }
        return if (streamResult.isSuccess) {
            Result.success(files)
        } else {
            Result.failure(streamResult.exceptionOrNull() ?: Exception("WebDAV operation failed"))
        }
    }

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
                file.name.getExtension(),
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

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return "/"
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun normalizeDirectoryPath(path: String): String {
        val normalized = normalizePath(path)
        return if (normalized.length > 1) normalized.trimEnd('/') else normalized
    }

    private fun isSamePath(pathA: String, pathB: String): Boolean {
        return normalizeDirectoryPath(pathA) == normalizeDirectoryPath(pathB)
    }
}
