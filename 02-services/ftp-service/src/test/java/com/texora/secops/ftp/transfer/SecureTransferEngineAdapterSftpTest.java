package com.texora.secops.ftp.transfer;

import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.security.TransferCredential;
import com.texora.secops.ftp.transfer.client.DeliveryResult;
import com.texora.secops.ftp.transfer.client.FetchResult;
import com.texora.secops.ftp.transfer.client.SecureTransferEngineAdapter;
import com.texora.secops.ftp.transfer.client.TransferTarget;
import com.texora.secops.ftp.transfer.client.TransferTransportException;
import com.texora.secops.ftp.transfer.storage.Digests;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the REAL adapter against an embedded Apache MINA SFTP server: full fetch, RESUME from a partial file,
 * host-key pinning, auth failure, size limit, path traversal, SSRF guard, overwrite refusal, abort and connection loss.
 */
class SecureTransferEngineAdapterSftpTest {

    private static SshServer server;
    private static Path serverRoot;
    private static String fingerprint;

    @TempDir
    Path work;

    private SecureTransferEngineAdapter adapter;
    private final TransferCredential good = new TransferCredential("ftpuser", "secret", null);

    @BeforeAll
    static void startServer() throws Exception {
        serverRoot = Files.createTempDirectory("sftp-root");
        Files.createDirectories(serverRoot.resolve("inbox"));
        Files.createDirectories(serverRoot.resolve("outbox"));
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1");
        server.setPort(0);
        server.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        server.setPasswordAuthenticator((user, pass, session) -> "ftpuser".equals(user) && "secret".equals(pass));
        server.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        server.setFileSystemFactory(new VirtualFileSystemFactory(serverRoot));
        server.start();
        KeyPair kp = server.getKeyPairProvider().loadKeys(null).iterator().next();
        fingerprint = KeyUtils.getFingerPrint(kp.getPublic());
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.stop(true);
    }

    @BeforeEach
    void setUp() {
        adapter = adapterAllowingLoopback(true);
    }

    private static SecureTransferEngineAdapter adapterAllowingLoopback(boolean allow) {
        FtpProperties props = new FtpProperties();
        props.getTransfer().setAllowLoopbackTargets(allow);
        props.getTransfer().setConnectTimeoutMs(5000);
        props.getTransfer().setIoTimeoutMs(10000);
        return new SecureTransferEngineAdapter(props);
    }

    private static TransferTarget target(String basePath, String fp) {
        return new TransferTarget("SFTP", "127.0.0.1", server.getPort(), basePath, fp);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        new Random(42).nextBytes(b);
        return b;
    }

    private static String sha256(byte[] b) throws Exception {
        return Digests.hex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private static TransferTransportException failure(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(call);
        assertThat(t).isInstanceOf(TransferTransportException.class);
        return (TransferTransportException) t;
    }

    @Test
    void fetchesAFileAndReportsItsSha256() throws Exception {
        byte[] content = randomBytes(300_000);
        Files.write(serverRoot.resolve("inbox/full.bin"), content);
        Path part = work.resolve("payload.part");

        FetchResult r = adapter.fetchToStaging(target("/inbox", fingerprint), good, "full.bin", part, 10_000_000L, () -> false);

        assertThat(r.getBytes()).isEqualTo(content.length);
        assertThat(r.getResumedFromOffset()).isZero();
        assertThat(r.getSha256()).isEqualTo(sha256(content));
        assertThat(Files.readAllBytes(part)).isEqualTo(content);
    }

    @Test
    void resumesFromAPartialFileAndStillProducesTheCorrectDigest() throws Exception {
        byte[] content = randomBytes(300_000);
        Files.write(serverRoot.resolve("inbox/resume.bin"), content);
        Path part = work.resolve("payload.part");
        Files.write(part, Arrays.copyOf(content, 123_456));    // simulated earlier interrupted download

        FetchResult r = adapter.fetchToStaging(target("/inbox", fingerprint), good, "resume.bin", part, 10_000_000L, () -> false);

        assertThat(r.getResumedFromOffset()).isEqualTo(123_456);
        assertThat(r.getBytes()).isEqualTo(content.length);
        assertThat(r.getSha256()).isEqualTo(sha256(content));
        assertThat(Files.readAllBytes(part)).isEqualTo(content);
    }

    @Test
    void aPartialFileLongerThanTheRemoteFileIsDiscardedAndTheDownloadRestarts() throws Exception {
        byte[] content = randomBytes(1_000);
        Files.write(serverRoot.resolve("inbox/short.bin"), content);
        Path part = work.resolve("payload.part");
        Files.write(part, randomBytes(5_000));

        FetchResult r = adapter.fetchToStaging(target("/inbox", fingerprint), good, "short.bin", part, 1_000_000L, () -> false);

        assertThat(r.getResumedFromOffset()).isZero();
        assertThat(r.getSha256()).isEqualTo(sha256(content));
        assertThat(Files.readAllBytes(part)).isEqualTo(content);
    }

    @Test
    void aWrongHostKeyFingerprintFailsClosedAndIsNotRetriable() throws Exception {
        Files.write(serverRoot.resolve("inbox/pin.bin"), randomBytes(10));
        String wrong = "SHA256:" + "A".repeat(43);
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", wrong), good, "pin.bin",
                work.resolve("p"), 1000, () -> false));
        assertThat(e.getCode()).isEqualTo("HOST_KEY_MISMATCH");
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void aMissingHostKeyPinFailsClosed() throws Exception {
        Files.write(serverRoot.resolve("inbox/nopin.bin"), randomBytes(10));
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", null), good, "nopin.bin",
                work.resolve("p"), 1000, () -> false));
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void badCredentialsAreFatal() throws Exception {
        Files.write(serverRoot.resolve("inbox/auth.bin"), randomBytes(10));
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", fingerprint),
                new TransferCredential("ftpuser", "wrong", null), "auth.bin", work.resolve("p"), 1000, () -> false));
        assertThat(e.getCode()).isEqualTo("AUTH_FAILED");
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void aMissingRemoteFileIsFatal() {
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", fingerprint), good,
                "does-not-exist.bin", work.resolve("p"), 1000, () -> false));
        assertThat(e.getCode()).isEqualTo("REMOTE_FILE_NOT_FOUND");
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void filesOverTheConfiguredLimitAreRejected() throws Exception {
        Files.write(serverRoot.resolve("inbox/big.bin"), randomBytes(5_000));
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", fingerprint), good, "big.bin",
                work.resolve("p"), 1_000, () -> false));
        assertThat(e.getCode()).isEqualTo("FILE_TOO_LARGE");
        assertThat(Files.exists(work.resolve("p"))).isFalse();
    }

    @Test
    void pathTraversalAndOddFileNamesAreRefused() {
        for (String bad : new String[]{"../etc/passwd", "a/b.txt", "..", ".hidden", "a b.txt", "x\\y", ""}) {
            TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", fingerprint), good, bad,
                    work.resolve("p"), 1000, () -> false));
            assertThat(e.getCode()).as(bad).isEqualTo("INVALID_FILE_NAME");
        }
    }

    @Test
    void aTraversalBasePathIsRefused() {
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox/../..", fingerprint), good, "a.bin",
                work.resolve("p"), 1000, () -> false));
        assertThat(e.getCode()).isEqualTo("INVALID_BASE_PATH");
    }

    @Test
    void loopbackTargetsAreForbiddenByDefault() {
        SecureTransferEngineAdapter strict = adapterAllowingLoopback(false);
        TransferTransportException e = failure(() -> strict.fetchToStaging(target("/inbox", fingerprint), good, "a.bin",
                work.resolve("p"), 1000, () -> false));
        assertThat(e.getCode()).isEqualTo("TARGET_ADDRESS_FORBIDDEN");
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void anAbortSignalStopsTheTransfer() throws Exception {
        Files.write(serverRoot.resolve("inbox/abort.bin"), randomBytes(10));
        TransferTransportException e = failure(() -> adapter.fetchToStaging(target("/inbox", fingerprint), good, "abort.bin",
                work.resolve("p"), 1000, () -> true));
        assertThat(e.isAborted()).isTrue();
        assertThat(e.isRetriable()).isFalse();
    }

    @Test
    void aClosedPortIsARetriableTransportError() throws Exception {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        TransferTarget dead = new TransferTarget("SFTP", "127.0.0.1", closedPort, "/inbox", fingerprint);
        TransferTransportException e = failure(() -> adapter.fetchToStaging(dead, good, "a.bin", work.resolve("p"), 1000, () -> false));
        assertThat(e.isRetriable()).isTrue();
    }

    @Test
    void deliversAStagedFileToTheDestination() throws Exception {
        byte[] content = randomBytes(200_000);
        Path staged = work.resolve("payload.staged");
        Files.write(staged, content);

        DeliveryResult r = adapter.deliverFromStaging(target("/outbox", fingerprint), good, "delivered.bin", staged, () -> false);

        assertThat(r.getBytes()).isEqualTo(content.length);
        assertThat(Files.readAllBytes(serverRoot.resolve("outbox/delivered.bin"))).isEqualTo(content);
        try (var files = Files.list(serverRoot.resolve("outbox"))) {
            assertThat(files.filter(p -> p.getFileName().toString().contains(".part-"))).isEmpty();
        }
    }

    @Test
    void neverOverwritesAnExistingDestinationFile() throws Exception {
        Files.write(serverRoot.resolve("outbox/exists.bin"), randomBytes(10));
        Path staged = work.resolve("payload.staged");
        Files.write(staged, randomBytes(20));

        TransferTransportException e = failure(() -> adapter.deliverFromStaging(target("/outbox", fingerprint), good,
                "exists.bin", staged, () -> false));

        assertThat(e.getCode()).isEqualTo("DESTINATION_EXISTS");
        assertThat(Files.size(serverRoot.resolve("outbox/exists.bin"))).isEqualTo(10);
    }

    @Test
    void deliveryHonoursHostKeyPinning() throws Exception {
        Path staged = work.resolve("payload.staged");
        Files.write(staged, randomBytes(20));
        TransferTransportException e = failure(() -> adapter.deliverFromStaging(target("/outbox", "SHA256:" + "B".repeat(43)),
                good, "pinned.bin", staged, () -> false));
        assertThat(e.getCode()).isEqualTo("HOST_KEY_MISMATCH");
        assertThat(Files.exists(serverRoot.resolve("outbox/pinned.bin"))).isFalse();
    }

    @Test
    void unsupportedProtocolIsRejected() {
        TransferTarget plainFtp = new TransferTarget("FTP", "127.0.0.1", 21, "/", null);
        TransferTransportException e = failure(() -> adapter.fetchToStaging(plainFtp, good, "a.bin", work.resolve("p"), 10, () -> false));
        assertThat(e.getCode()).isEqualTo("UNSUPPORTED_PROTOCOL");
    }
}
