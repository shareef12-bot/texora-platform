package com.texora.secops.ftp.transfer.client;

/**
 * Library-neutral failure raised by {@link SecureTransferEngineAdapter}. Callers never see SFTP/FTPS library types.
 * {@code retriable} tells the orchestrator whether a retry/resume could help; {@code aborted} means the job was
 * cancelled/quarantined while the transfer was running.
 */
public class TransferTransportException extends Exception {

    private final String code;
    private final boolean retriable;
    private final boolean aborted;

    private TransferTransportException(String code, String message, boolean retriable, boolean aborted, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retriable = retriable;
        this.aborted = aborted;
    }

    public static TransferTransportException retriable(String code, String message, Throwable cause) {
        return new TransferTransportException(code, message, true, false, cause);
    }

    public static TransferTransportException fatal(String code, String message, Throwable cause) {
        return new TransferTransportException(code, message, false, false, cause);
    }

    public static TransferTransportException aborted() {
        return new TransferTransportException("TRANSFER_ABORTED", "Transfer aborted: job no longer running", false, true, null);
    }

    public String getCode() {
        return code;
    }

    public boolean isRetriable() {
        return retriable;
    }

    public boolean isAborted() {
        return aborted;
    }
}
