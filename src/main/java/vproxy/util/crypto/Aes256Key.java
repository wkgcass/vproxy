package vproxy.util.crypto;

import javax.crypto.spec.SecretKeySpec;

public class Aes256Key implements BlockCipherKey {
    private final byte[] byteKey;

    public Aes256Key(String stringKey) {
        this.byteKey = Utils.getKey(stringKey, keyLen(), ivLen());
    }

    @Override
    public SecretKeySpec getSecretKeySpec() {
        return new SecretKeySpec(byteKey, "AES");
    }

    @Override
    public String cipherName() {
        return "AES/CFB/NoPadding";
    }

    @Override
    public int keyLen() {
        return 32;
    }

    @Override
    public int ivLen() {
        return blockSize();
    }

    @Override
    public int blockSize() {
        return 16;
    }
}
