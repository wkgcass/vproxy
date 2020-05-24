package vproxybase.util.crypto;

import vproxybase.util.LogType;
import vproxybase.util.Logger;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class StreamingCFBCipher {
    private final BlockCipherKey key;
    private final boolean encrypting;
    private final byte[] iv;
    private final byte[] buffer = new byte[16384];
    private int oldLen = 0;

    private Cipher cipher;

    public StreamingCFBCipher(BlockCipherKey key, boolean encrypting) {
        this(key, encrypting, null);
    }

    public StreamingCFBCipher(BlockCipherKey key, boolean encrypting, byte[] iv) {
        this.key = key;
        this.encrypting = encrypting;
        if (iv == null) {
            this.iv = CryptoUtils.randomBytes(key.ivLen());
        } else {
            if (iv.length != key.ivLen())
                throw new IllegalArgumentException("wrong iv length");
            this.iv = iv;
        }
        buildCipher();
    }

    private void buildCipher() {
        try {
            Cipher cipher = Cipher.getInstance(key.cipherName());
            cipher.init(encrypting ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key.getSecretKeySpec(), new IvParameterSpec(iv));
            this.cipher = cipher;
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException e) {
            Logger.error(LogType.IMPROPER_USE, "building cipher thrown exception", e);
            throw new RuntimeException("unrecoverable error, building cipher failed");
        }
    }

    public byte[] update(byte[] input, int inputOff, int inputLen) {
        if (inputOff < 0 || inputOff >= input.length)
            throw new IllegalArgumentException("off is wrong");
        if (inputLen < 0 || inputOff + inputLen > input.length)
            throw new IllegalArgumentException("len is wrong");
        if (inputLen == 0) {
            return new byte[0];
        }

        int newLen; // data read from input
        // oldLen; // data stored in buffer
        int ex; // extra padding for input data
        int allLen; // newLen + oldLen
        int totalLen; // newLen + oldLen + ex

        List<ByteBuffer> result = new ArrayList<>();
        while (true) {
            if (inputLen <= 0) {
                if (result.isEmpty()) {
                    throw new RuntimeException("result should not be empty");
                }
                // get result if got only one buffer
                if (result.size() == 1) {
                    ByteBuffer buf = result.get(0);
                    if (buf.position() == 0 && buf.limit() == buf.capacity()) {
                        return buf.array();
                    }
                    byte[] ret = new byte[buf.limit() - buf.position()];
                    buf.get(ret);
                    return ret;
                }
                // result
                int total = 0;
                for (ByteBuffer b : result) {
                    total += (b.limit() - b.position());
                }
                byte[] ret = new byte[total];
                int offset = 0;
                for (ByteBuffer b : result) {
                    int len = (b.limit() - b.position());
                    b.get(ret, offset, len);
                    offset += len;
                }
                return ret;
            }
            newLen = inputLen;
            if (newLen > buffer.length - oldLen) {
                newLen = buffer.length - oldLen;
            }
            allLen = newLen + oldLen;
            {
                ex = key.blockSize() - allLen % key.blockSize();
                if (ex == key.blockSize()) {
                    ex = 0;
                }
            }
            {
                System.arraycopy(input, inputOff, buffer, oldLen, newLen);
                inputOff += newLen;
                inputLen -= newLen;
            }

            totalLen = allLen + ex;
            assert totalLen % iv.length == 0;
            byte[] res = cipher.update(buffer, 0, totalLen);
            assert res.length == totalLen;

            result.add(ByteBuffer.wrap(res, oldLen, newLen));

            if (ex == 0) {
                // record iv for further use
                byte[] encryptedBuffer = encrypting ? res : buffer;
                System.arraycopy(encryptedBuffer, totalLen - iv.length, iv, 0, iv.length);
                // clear old len
                oldLen = 0;
            } else {
                if (allLen >= iv.length) {
                    assert allLen != iv.length; // because ex != 0, allLen should never equal to ivLen
                    // have enough bytes to build iv

                    // record iv #1
                    byte[] encryptedBuffer = encrypting ? res : buffer;
                    System.arraycopy(encryptedBuffer, totalLen - iv.length * 2, iv, 0, iv.length);

                    // record bytes to buffer #1
                    for (int i = totalLen - iv.length, j = 0; i < totalLen - ex; ++i, ++j) {
                        buffer[j] = buffer[i];
                    }
                }
                // record iv #2
                buildCipher();
                // record bytes to buffer #2
                oldLen = iv.length - ex;
            }
        }
    }
}
