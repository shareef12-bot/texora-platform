package com.texora.secops.ftp.security;

import java.util.Objects;

/**
 * Short-lived secret material fetched from SEC. toString() deliberately omits secrets.
 */
public class TransferCredential {

    private String username;

    private String password;

    private String privateKeyPem;

    public TransferCredential() {
    }

    public TransferCredential(String username, String password, String privateKeyPem) {
        this.username = username;
        this.password = password;
        this.privateKeyPem = privateKeyPem;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TransferCredential)) {
            return false;
        }
        TransferCredential that = (TransferCredential) other;
        return Objects.equals(username, that.username) && Objects.equals(password, that.password) && Objects.equals(privateKeyPem, that.privateKeyPem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password, privateKeyPem);
    }

    @Override
    public String toString() {
        return "TransferCredential{" + "username=" + username + "}";
    }
}
