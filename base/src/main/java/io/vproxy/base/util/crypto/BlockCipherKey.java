package vproxy.base.util.crypto;

import javax.crypto.spec.SecretKeySpec;

public interface BlockCipherKey {
    SecretKeySpec getSecretKeySpec();

    String cipherName();

    int keyLen();

    int ivLen();

    int blockSize();
}
