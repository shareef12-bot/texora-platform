package com.texora.secops.ftp.transfer.client;

import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.security.TransferCredential;
import com.texora.secops.ftp.transfer.storage.Digests;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import org.apache.commons.net.util.TrustManagerUtils;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.ServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.common.SftpConstants;
import org.apache.sshd.sftp.common.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * THE isolation seam (Shared Standards B.4): the ONLY class in this service that speaks SFTP/FTPS and the ONLY class
 * allowed to import org.apache.sshd.* or org.apache.commons.net.*. An architecture test enforces that.
 *
 * NO custom transport cryptography: SFTP is Apache MINA SSHD, FTPS is Apache Commons Net over the JVM's TLS stack.
 *  - SFTP: the server host key MUST match the pinned fingerprint registered with the endpoint; otherwise fail closed.
 *  - FTPS: explicit TLS, full certificate-chain + hostname validation against the JVM trust store, PBSZ 0 / PROT P.
 *  - Plain FTP is not supported at all.
 *  - Targets that resolve to loopback / link-local / any-local / multicast addresses are refused (SSRF guard).
 *
 * Nothing here executes, interprets or opens fetched content: bytes are streamed to disk and hashed, nothing more.
 * All failures are reported as {@link TransferTransportException}; credentials are never logged.
 */
@Component
public class SecureTransferEngineAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecureTransferEngineAdapter.class);
    private static final Pattern FILE_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$");
    private static final int BUFFER_SIZE = 64 * 1024;

    private final FtpProperties props;

    public SecureTransferEngineAdapter(FtpProperties props) {
        this.props = props;
    }

    // =====================================================================================================
    // Public API
    // =====================================================================================================

    /**
     * Downloads {@code fileName} from the source target into {@code partFile}, RESUMING from any bytes already
     * present in {@code partFile}. The returned digest covers the complete file (resumed prefix + new bytes).
     */
    public FetchResult fetchToStaging(TransferTarget target, TransferCredential credential, String fileName,
                                      Path partFile, long maxBytes, BooleanSupplier abort) throws TransferTransportException {
        String remote = remotePath(target, fileName);
        InetAddress address = guardTarget(target);
        if ("SFTP".equals(target.getProtocol())) {
            return fetchSftp(target, address, credential, remote, partFile, maxBytes, abort);
        }
        if ("FTPS".equals(target.getProtocol())) {
            return fetchFtps(target, credential, remote, partFile, maxBytes, abort);
        }
        throw TransferTransportException.fatal("UNSUPPORTED_PROTOCOL", "Unsupported protocol", null);
    }

    /** Uploads the staged file to the destination target. Refuses to overwrite an existing remote file. */
    public DeliveryResult deliverFromStaging(TransferTarget target, TransferCredential credential, String fileName,
                                             Path stagedFile, BooleanSupplier abort) throws TransferTransportException {
        String remote = remotePath(target, fileName);
        InetAddress address = guardTarget(target);
        if ("SFTP".equals(target.getProtocol())) {
            return deliverSftp(target, address, credential, remote, stagedFile, abort);
        }
        if ("FTPS".equals(target.getProtocol())) {
            return deliverFtps(target, credential, remote, stagedFile, abort);
        }
        throw TransferTransportException.fatal("UNSUPPORTED_PROTOCOL", "Unsupported protocol", null);
    }

    // =====================================================================================================
    // SFTP
    // =====================================================================================================

    private FetchResult fetchSftp(TransferTarget t, InetAddress address, TransferCredential c, String remote, Path part,
                                  long maxBytes, BooleanSupplier abort) throws TransferTransportException {
        SshClient client = newSshClient();
        AtomicBoolean hostKeyRejected = new AtomicBoolean(false);
        client.setServerKeyVerifier(pinnedVerifier(t.getHostKeyFingerprint(), hostKeyRejected));
        client.start();
        try (ClientSession session = openSession(client, t, address, c, hostKeyRejected);
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            long remoteSize = sftp.stat(remote).getSize();
            if (remoteSize > maxBytes) {
                throw TransferTransportException.fatal("FILE_TOO_LARGE", "Remote file exceeds the configured maximum size", null);
            }
            MessageDigest digest = Digests.newSha256();
            long offset = prepareResume(part, remoteSize, digest);
            long resumedFrom = offset;
            AbortCheck check = new AbortCheck(abort);
            check.tick(0);
            try (SftpClient.CloseableHandle handle = sftp.open(remote, EnumSet.of(SftpClient.OpenMode.Read));
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                byte[] buf = new byte[BUFFER_SIZE];
                while (offset < remoteSize) {
                    int n = sftp.read(handle, offset, buf);
                    if (n < 0) {
                        break;
                    }
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                    offset += n;
                    check.tick(n);
                }
            }
            if (offset != remoteSize) {
                throw TransferTransportException.retriable("SHORT_READ", "Connection ended before the full file was read", null);
            }
            return new FetchResult(offset, Digests.hex(digest.digest()), resumedFrom);
        } catch (IOException e) {
            throw classify(e, hostKeyRejected);
        } finally {
            client.stop();
        }
    }

    private DeliveryResult deliverSftp(TransferTarget t, InetAddress address, TransferCredential c, String remote,
                                       Path staged, BooleanSupplier abort) throws TransferTransportException {
        SshClient client = newSshClient();
        AtomicBoolean hostKeyRejected = new AtomicBoolean(false);
        client.setServerKeyVerifier(pinnedVerifier(t.getHostKeyFingerprint(), hostKeyRejected));
        client.start();
        try (ClientSession session = openSession(client, t, address, c, hostKeyRejected);
             SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
            if (sftpExists(sftp, remote)) {
                throw TransferTransportException.fatal("DESTINATION_EXISTS", "Destination file already exists; refusing to overwrite", null);
            }
            String temp = remote + ".part-" + UUID.randomUUID();
            long offset = 0;
            try {
                AbortCheck check = new AbortCheck(abort);
                check.tick(0);
                try (SftpClient.CloseableHandle handle = sftp.open(temp,
                        EnumSet.of(SftpClient.OpenMode.Write, SftpClient.OpenMode.Create, SftpClient.OpenMode.Exclusive));
                     InputStream in = Files.newInputStream(staged)) {
                    byte[] buf = new byte[BUFFER_SIZE];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        sftp.write(handle, offset, buf, 0, n);
                        offset += n;
                        check.tick(n);
                    }
                }
                sftp.rename(temp, remote);
            } catch (IOException | TransferTransportException e) {
                quietRemove(sftp, temp);
                throw e;
            }
            return new DeliveryResult(offset);
        } catch (IOException e) {
            throw classify(e, hostKeyRejected);
        } finally {
            client.stop();
        }
    }

    private ClientSession openSession(SshClient client, TransferTarget t, InetAddress address, TransferCredential c,
                                      AtomicBoolean hostKeyRejected) throws IOException, TransferTransportException {
        Duration timeout = Duration.ofMillis(props.getTransfer().getConnectTimeoutMs());
        // Connect to the address we already vetted (not a second DNS lookup) to blunt DNS-rebinding.
        ClientSession session = client.connect(c.getUsername(), address.getHostAddress(), t.getPort())
                .verify(timeout).getSession();
        try {
            if (c.getPassword() != null) {
                session.addPasswordIdentity(c.getPassword());
            }
            if (c.getPrivateKeyPem() != null) {
                Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(session, NamedResource.ofName("transfer-key"),
                        new ByteArrayInputStream(c.getPrivateKeyPem().getBytes(StandardCharsets.UTF_8)),
                        FilePasswordProvider.EMPTY);
                if (keys != null) {
                    for (KeyPair kp : keys) {
                        session.addPublicKeyIdentity(kp);
                    }
                }
            }
            session.auth().verify(timeout);
            return session;
        } catch (GeneralSecurityException e) {
            session.close(true);
            throw TransferTransportException.fatal("AUTH_FAILED", "Could not load the SFTP identity", e);
        } catch (IOException | RuntimeException e) {
            session.close(true);
            throw e;
        }
    }

    private SshClient newSshClient() {
        SshClient client = SshClient.setUpDefaultClient();
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMillis(props.getTransfer().getIoTimeoutMs()));
        return client;
    }

    /** Host-key pinning: accept ONLY the fingerprint registered for the endpoint; no pin means reject. */
    private static ServerKeyVerifier pinnedVerifier(String expectedFingerprint, AtomicBoolean rejected) {
        return (ClientSession session, SocketAddress remote, PublicKey serverKey) -> {
            boolean ok = false;
            if (expectedFingerprint != null && !expectedFingerprint.isBlank()) {
                String actual = normaliseFingerprint(KeyUtils.getFingerPrint(serverKey));
                ok = MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                        normaliseFingerprint(expectedFingerprint).getBytes(StandardCharsets.UTF_8));
            }
            if (!ok) {
                rejected.set(true);
            }
            return ok;
        };
    }

    private static String normaliseFingerprint(String fp) {
        String s = fp.trim();
        while (s.endsWith("=")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static boolean sftpExists(SftpClient sftp, String path) throws IOException {
        try {
            sftp.stat(path);
            return true;
        } catch (SftpException e) {
            if (e.getStatus() == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                return false;
            }
            throw e;
        }
    }

    private static void quietRemove(SftpClient sftp, String path) {
        try {
            sftp.remove(path);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not remove temporary remote file: {}", e.toString());
        }
    }

    private static TransferTransportException classify(IOException e, AtomicBoolean hostKeyRejected) {
        if (hostKeyRejected.get()) {
            return TransferTransportException.fatal("HOST_KEY_MISMATCH", "Server host key does not match the pinned fingerprint", e);
        }
        if (e instanceof SftpException) {
            int status = ((SftpException) e).getStatus();
            if (status == SftpConstants.SSH_FX_NO_SUCH_FILE) {
                return TransferTransportException.fatal("REMOTE_FILE_NOT_FOUND", "Remote file not found", e);
            }
            if (status == SftpConstants.SSH_FX_PERMISSION_DENIED) {
                return TransferTransportException.fatal("REMOTE_PERMISSION_DENIED", "Remote permission denied", e);
            }
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("authentication")) {
            return TransferTransportException.fatal("AUTH_FAILED", "Authentication to the remote endpoint failed", e);
        }
        return TransferTransportException.retriable("TRANSPORT_ERROR", "Transport error: " + e.getClass().getSimpleName(), e);
    }

    // =====================================================================================================
    // FTPS (explicit TLS)
    // =====================================================================================================

    private FetchResult fetchFtps(TransferTarget t, TransferCredential c, String remote, Path part, long maxBytes,
                                  BooleanSupplier abort) throws TransferTransportException {
        FTPSClient ftps = new FTPSClient("TLS", false);
        try {
            connectFtps(ftps, t, c);
            long remoteSize = parseSize(ftps.getSize(remote));
            if (remoteSize > maxBytes) {
                throw TransferTransportException.fatal("FILE_TOO_LARGE", "Remote file exceeds the configured maximum size", null);
            }
            MessageDigest digest = Digests.newSha256();
            long offset = prepareResume(part, remoteSize < 0 ? Long.MAX_VALUE : remoteSize, digest);
            long resumedFrom = offset;
            if (offset > 0) {
                ftps.setRestartOffset(offset);
            }
            InputStream stream = ftps.retrieveFileStream(remote);
            if (stream == null) {
                throw ftpsReplyError(ftps);
            }
            AbortCheck check = new AbortCheck(abort);
            check.tick(0);
            try (InputStream in = stream;
                 OutputStream out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    out.write(buf, 0, n);
                    digest.update(buf, 0, n);
                    offset += n;
                    if (offset > maxBytes) {
                        throw TransferTransportException.fatal("FILE_TOO_LARGE", "Remote file exceeds the configured maximum size", null);
                    }
                    check.tick(n);
                }
            }
            if (!ftps.completePendingCommand()) {
                throw TransferTransportException.retriable("TRANSFER_INCOMPLETE", "FTPS transfer did not complete cleanly", null);
            }
            if (remoteSize >= 0 && offset != remoteSize) {
                throw TransferTransportException.retriable("SHORT_READ", "Connection ended before the full file was read", null);
            }
            return new FetchResult(offset, Digests.hex(digest.digest()), resumedFrom);
        } catch (IOException e) {
            throw TransferTransportException.retriable("TRANSPORT_ERROR", "Transport error: " + e.getClass().getSimpleName(), e);
        } finally {
            disconnectQuietly(ftps);
        }
    }

    private DeliveryResult deliverFtps(TransferTarget t, TransferCredential c, String remote, Path staged,
                                       BooleanSupplier abort) throws TransferTransportException {
        FTPSClient ftps = new FTPSClient("TLS", false);
        String temp = remote + ".part-" + UUID.randomUUID();
        try {
            connectFtps(ftps, t, c);
            if (ftps.getSize(remote) != null) {
                throw TransferTransportException.fatal("DESTINATION_EXISTS", "Destination file already exists; refusing to overwrite", null);
            }
            AbortCheck check = new AbortCheck(abort);
            check.tick(0);
            long size = Files.size(staged);
            try (InputStream in = new AbortingInputStream(Files.newInputStream(staged), check)) {
                if (!ftps.storeFile(temp, in)) {
                    throw ftpsReplyError(ftps);
                }
            }
            if (!ftps.rename(temp, remote)) {
                ftps.deleteFile(temp);
                throw ftpsReplyError(ftps);
            }
            return new DeliveryResult(size);
        } catch (AbortSignal e) {
            quietDelete(ftps, temp);
            throw TransferTransportException.aborted();
        } catch (IOException e) {
            quietDelete(ftps, temp);
            throw TransferTransportException.retriable("TRANSPORT_ERROR", "Transport error: " + e.getClass().getSimpleName(), e);
        } catch (TransferTransportException e) {
            quietDelete(ftps, temp);
            throw e;
        } finally {
            disconnectQuietly(ftps);
        }
    }

    private void connectFtps(FTPSClient ftps, TransferTarget t, TransferCredential c)
            throws IOException, TransferTransportException {
        if (c.getPassword() == null) {
            throw TransferTransportException.fatal("AUTH_FAILED", "FTPS requires a password credential", null);
        }
        try {
            // Default FTPSClient only checks validity dates. Use the JVM default trust store (full chain validation).
            ftps.setTrustManager(TrustManagerUtils.getDefaultTrustManager(null));
        } catch (GeneralSecurityException e) {
            throw TransferTransportException.fatal("TLS_SETUP_FAILED", "Could not initialise TLS trust", e);
        }
        ftps.setEndpointCheckingEnabled(true);
        ftps.setConnectTimeout(props.getTransfer().getConnectTimeoutMs());
        ftps.setDefaultTimeout(props.getTransfer().getIoTimeoutMs());
        ftps.connect(t.getHost(), t.getPort());
        if (!FTPReply.isPositiveCompletion(ftps.getReplyCode())) {
            throw TransferTransportException.retriable("CONNECT_REFUSED", "FTPS server refused the connection", null);
        }
        ftps.setSoTimeout(props.getTransfer().getIoTimeoutMs());
        if (!ftps.login(c.getUsername(), c.getPassword())) {
            throw TransferTransportException.fatal("AUTH_FAILED", "Authentication to the remote endpoint failed", null);
        }
        ftps.execPBSZ(0);
        ftps.execPROT("P");
        ftps.enterLocalPassiveMode();
        ftps.setFileType(FTP.BINARY_FILE_TYPE);
        ftps.setDataTimeout(Duration.ofMillis(props.getTransfer().getIoTimeoutMs()));
    }

    private static TransferTransportException ftpsReplyError(FTPSClient ftps) {
        int code = ftps.getReplyCode();
        if (code == 550) {
            return TransferTransportException.fatal("REMOTE_FILE_NOT_FOUND", "Remote file not found or not permitted", null);
        }
        if (code == 530) {
            return TransferTransportException.fatal("AUTH_FAILED", "Authentication to the remote endpoint failed", null);
        }
        return TransferTransportException.retriable("FTPS_REPLY_" + code, "FTPS server returned reply " + code, null);
    }

    private static long parseSize(String reply) {
        if (reply == null) {
            return -1L;
        }
        try {
            return Long.parseLong(reply.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static void quietDelete(FTPSClient ftps, String path) {
        try {
            if (ftps.isConnected()) {
                ftps.deleteFile(path);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Could not remove temporary remote file: {}", e.toString());
        }
    }

    private static void disconnectQuietly(FTPSClient ftps) {
        try {
            if (ftps.isConnected()) {
                ftps.logout();
                ftps.disconnect();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("FTPS disconnect: {}", e.toString());
        }
    }

    // =====================================================================================================
    // Shared helpers
    // =====================================================================================================

    /**
     * Primes {@code digest} with the bytes of an existing partial file and returns the offset to resume from.
     * A partial file longer than the remote file cannot be a valid prefix, so it is discarded and the download restarts.
     */
    private static long prepareResume(Path part, long remoteSize, MessageDigest digest) throws IOException {
        if (!Files.exists(part)) {
            return 0L;
        }
        long size = Files.size(part);
        if (size > remoteSize) {
            Files.delete(part);
            return 0L;
        }
        try (InputStream in = Files.newInputStream(part)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        return size;
    }

    private String remotePath(TransferTarget t, String fileName) throws TransferTransportException {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches() || fileName.contains("..")) {
            throw TransferTransportException.fatal("INVALID_FILE_NAME", "File name is not permitted", null);
        }
        String base = t.getBasePath() == null || t.getBasePath().isBlank() ? "/" : t.getBasePath();
        if (!base.startsWith("/") || base.contains("..") || base.indexOf('\\') >= 0 || base.indexOf('\0') >= 0) {
            throw TransferTransportException.fatal("INVALID_BASE_PATH", "Endpoint base path is not permitted", null);
        }
        return base.endsWith("/") ? base + fileName : base + "/" + fileName;
    }

    /** Resolves the host and refuses internal address ranges (SSRF guard). Returns the vetted address. */
    private InetAddress guardTarget(TransferTarget t) throws TransferTransportException {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(t.getHost());
        } catch (UnknownHostException e) {
            throw TransferTransportException.retriable("DNS_FAILURE", "Could not resolve the remote host", e);
        }
        for (InetAddress a : addresses) {
            boolean forbidden = a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress() || a.isMulticastAddress();
            if (forbidden && !props.getTransfer().isAllowLoopbackTargets()) {
                throw TransferTransportException.fatal("TARGET_ADDRESS_FORBIDDEN", "Target resolves to a forbidden address range", null);
            }
        }
        return addresses[0];
    }

    /** Polls the cancel/quarantine signal at most every ~1 MiB or ~2 s so it never costs a lookup per chunk. */
    private static final class AbortCheck {
        private final BooleanSupplier signal;
        private long bytesSince;
        private long lastNanos = System.nanoTime();

        AbortCheck(BooleanSupplier signal) {
            this.signal = signal;
        }

        void tick(int bytes) throws TransferTransportException {
            bytesSince += bytes;
            long now = System.nanoTime();
            if (bytes == 0 || bytesSince >= (1 << 20) || now - lastNanos > 2_000_000_000L) {
                bytesSince = 0;
                lastNanos = now;
                if (signal.getAsBoolean()) {
                    throw TransferTransportException.aborted();
                }
            }
        }
    }

    private static final class AbortSignal extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class AbortingInputStream extends FilterInputStream {
        private final AbortCheck check;

        AbortingInputStream(InputStream in, AbortCheck check) {
            super(in);
            this.check = check;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                try {
                    check.tick(n);
                } catch (TransferTransportException e) {
                    throw new AbortSignal();
                }
            }
            return n;
        }
    }
}
