package com.texora.secops.ftp.transfer.storage;

import com.texora.secops.ftp.config.FtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Local staging area for payload bytes (Java NIO). Layout: {root}/{tenantId}/{jobId}/payload.part|payload.staged.
 * Directory names are UUIDs only, so no caller-supplied string ever reaches the filesystem path.
 * Staged files are owner-read/write ONLY (no execute bit) and are never executed, parsed or opened by this service;
 * mount the volume noexec in production. The metadata store references these files by path/URI only.
 */
@Component
public class StagingStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(StagingStorage.class);
    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private final Path root;

    public StagingStorage(FtpProperties props) {
        this.root = Paths.get(props.getStaging().getDirectory()).toAbsolutePath().normalize();
    }

    public Path jobDir(UUID tenantId, UUID jobId) {
        Path dir = root.resolve(tenantId.toString()).resolve(jobId.toString()).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalStateException("Staging path escaped root");
        }
        return dir;
    }

    /** Creates the job directory (owner-only) and returns the partial-download path. */
    public Path partFile(UUID tenantId, UUID jobId) throws IOException {
        Path dir = jobDir(tenantId, jobId);
        if (POSIX) {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectories(dir);
        }
        return dir.resolve("payload.part");
    }

    public Path stagedFile(UUID tenantId, UUID jobId) {
        return jobDir(tenantId, jobId).resolve("payload.staged");
    }

    /** Atomically turns a completed partial download into the staged file and strips any execute permission. */
    public Path promote(UUID tenantId, UUID jobId) throws IOException {
        Path dir = jobDir(tenantId, jobId);
        Path staged = dir.resolve("payload.staged");
        Files.move(dir.resolve("payload.part"), staged, StandardCopyOption.ATOMIC_MOVE);
        if (POSIX) {
            Files.setPosixFilePermissions(staged, PosixFilePermissions.fromString("rw-------"));
        }
        return staged;
    }

    public boolean exists(Path file) {
        return Files.isRegularFile(file);
    }

    /** Independent re-hash of the bytes as they sit on disk. */
    public String sha256(Path file) throws IOException {
        MessageDigest digest = Digests.newSha256();
        byte[] buf = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) >= 0) {
                digest.update(buf, 0, n);
            }
        }
        return Digests.hex(digest.digest());
    }

    public String uriFor(UUID tenantId, UUID jobId) {
        return stagedFile(tenantId, jobId).toUri().toString();
    }

    /** Deletes the job's staging directory. Best effort; failures are logged, never thrown. */
    public void destroy(UUID tenantId, UUID jobId) {
        Path dir = jobDir(tenantId, jobId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete staging path {}: {}", p, e.toString());
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Could not clean staging directory for job {}: {}", jobId, e.toString());
        }
    }
}
