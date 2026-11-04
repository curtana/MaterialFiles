/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import java8.nio.channels.SeekableByteChannel
import me.zhanghai.android.files.provider.common.LocalWatchService
import me.zhanghai.android.files.provider.common.NotifyEntryModifiedSeekableByteChannel
import me.zhanghai.android.files.util.closeSafe
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import java8.nio.file.Path as Java8Path

object Client {
    @Volatile
    lateinit var authenticator: Authenticator

    private val clients = mutableMapOf<Authority, SFTPClient>()

    private val sshClients = mutableMapOf<Authority, SSHClient>()

    private val directoryFileAttributesCache =
        Collections.synchronizedMap(WeakHashMap<Path, FileAttributes>())

    @Throws(ClientException::class)
    fun access(path: Path, flags: Set<OpenMode>) {
        withClient(path.authority) { it.open(path.remotePath, flags, FileAttributes.EMPTY).close() }
    }

    @Throws(ClientException::class)
    fun lstat(path: Path): FileAttributes =
        withClient(path.authority) { client ->
            synchronized(directoryFileAttributesCache) {
                directoryFileAttributesCache[path]?.let {
                    return it.also { directoryFileAttributesCache -= path }
                }
            }
            client.lstat(path.remotePath)
        }

    @Throws(ClientException::class)
    fun mkdir(path: Path, attributes: FileAttributes) {
        withClient(path.authority) { it.sftpEngine.makeDir(path.remotePath, attributes) }
        LocalWatchService.onEntryCreated(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun openByteChannel(
        path: Path,
        flags: Set<OpenMode>,
        attributes: FileAttributes
    ): SeekableByteChannel =
        withClient(path.authority) { client ->
            val file = client.open(path.remotePath, flags, attributes)
            NotifyEntryModifiedSeekableByteChannel(
                FileByteChannel(file, flags.contains(OpenMode.APPEND)) {
                    evictClient(path.authority, client)
                },
                path as Java8Path
            )
        }

    @Throws(ClientException::class)
    fun readlink(path: Path): String = withClient(path.authority) { it.readlink(path.remotePath) }

    @Throws(ClientException::class)
    fun realpath(path: Path): Path {
        val realPath = withClient(path.authority) { it.canonicalize(path.remotePath) }
        return path.resolve(realPath)
    }

    @Throws(ClientException::class)
    fun remove(path: Path) {
        val attributes = lstat(path)
        val isDirectory = attributes.type == FileMode.Type.DIRECTORY
        if (isDirectory) {
            rmdir(path)
        } else {
            unlink(path)
        }
    }

    // Note that unlike POSIX rename(), this won't overwrite an existing file.
    @Throws(ClientException::class)
    fun rename(path: Path, newPath: Path) {
        if (newPath.authority != path.authority) {
            throw ClientException(
                SFTPException(Response.StatusCode.FAILURE, "Paths aren't on the same authority")
            )
        }
        withClient(path.authority) { it.rename(path.remotePath, newPath.remotePath) }
        directoryFileAttributesCache -= path
        directoryFileAttributesCache -= newPath
        LocalWatchService.onEntryDeleted(path as Java8Path)
        LocalWatchService.onEntryCreated(newPath as Java8Path)
    }

    @Throws(ClientException::class)
    fun rmdir(path: Path) {
        withClient(path.authority) { it.rmdir(path.remotePath) }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun scandir(path: Path): List<Path> {
        val files = withClient(path.authority) { it.ls(path.remotePath) }
        return files.map { file ->
            // The attributes here are from lstat().
            // https://github.com/openssh/openssh-portable/blob/71241fc05db4bbb11bb29340b44b92e2575373d8/sftp-server.c#L1110
            path.resolve(file.name).also { directoryFileAttributesCache[it] = file.attributes }
        }
    }

    @Throws(ClientException::class)
    fun setstat(path: Path, attributes: FileAttributes) {
        withClient(path.authority) { it.setattr(path.remotePath, attributes) }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryModified(path as Java8Path)
    }

    @Throws(ClientException::class)
    fun stat(path: Path): FileAttributes =
        withClient(path.authority) { client ->
            synchronized(directoryFileAttributesCache) {
                directoryFileAttributesCache[path]?.let {
                    if (it.type != FileMode.Type.SYMLINK) {
                        return it.also { directoryFileAttributesCache -= path }
                    }
                }
            }
            client.stat(path.remotePath)
        }

    @Throws(ClientException::class)
    fun symlink(link: Path, target: String) {
        withClient(link.authority) { it.symlink(link.remotePath, target) }
        LocalWatchService.onEntryCreated(link as Java8Path)
    }

    @Throws(ClientException::class)
    fun unlink(path: Path) {
        withClient(path.authority) { it.rm(path.remotePath) }
        directoryFileAttributesCache -= path
        LocalWatchService.onEntryDeleted(path as Java8Path)
    }

    @Throws(ClientException::class)
    private inline fun <R> withClient(authority: Authority, block: (SFTPClient) -> R): R =
        getClient(authority).callOrEvict({ evictClient(authority, it) }, block)

    @Throws(ClientException::class)
    private fun getClient(authority: Authority): SFTPClient {
        synchronized(clients) {
            var client = clients[authority]
            if (client != null) {
                if (client.sftpEngine.subsystem.isOpen) {
                    return client
                } else {
                    client.closeSafe()
                    clients -= authority
                    sshClients.remove(authority)?.closeSafe()
                }
            }
            val authentication = authenticator.getAuthentication(authority)
                ?: throw ClientException("No authentication found for $authority")
            val sshClient = SSHClient().apply { addHostKeyVerifier(PromiscuousVerifier()) }
            try {
                sshClient.connect(authority.host, authority.port)
            } catch (e: IOException) {
                sshClient.closeSafe()
                throw ClientException(e)
            }
            try {
                sshClient.auth(authority.username, authentication.toAuthMethod())
            } catch (e: UserAuthException) {
                sshClient.closeSafe()
                throw ClientException(e)
            } catch (e: TransportException) {
                sshClient.closeSafe()
                throw ClientException(e)
            }
            client = sshClient.newSFTPClient()
            clients[authority] = client
            sshClients[authority] = sshClient
            return client
        }
    }

    // A request that failed with an unexpected exception may have left the connection in a broken
    // state (e.g. a half-written packet), so the next request should use a new connection.
    private fun evictClient(authority: Authority, client: SFTPClient) {
        val sshClient = synchronized(clients) {
            if (clients[authority] !== client) {
                // Already evicted.
                return
            }
            clients -= authority
            sshClients.remove(authority)
        }
        // Disconnecting the transport also closes its channels, without waiting for the server.
        sshClient?.closeSafe()
    }

    interface Path {
        val authority: Authority
        val remotePath: String
        fun resolve(other: String): Path
    }
}

/**
 * Calls [block] with this client and converts its exceptions to [ClientException].
 *
 * An exception other than [IOException] (e.g. a `NetworkOnMainThreadException` from the socket,
 * or an [IndexOutOfBoundsException] from a buffer left inconsistent by an earlier failure) means
 * that the client can no longer be trusted, so [evict] is called before rethrowing.
 */
@Throws(ClientException::class)
internal inline fun <C, R> C.callOrEvict(evict: (C) -> Unit, block: (C) -> R): R =
    try {
        block(this)
    } catch (e: IOException) {
        throw ClientException(e)
    } catch (e: RuntimeException) {
        evict(this)
        throw ClientException(e)
    }
