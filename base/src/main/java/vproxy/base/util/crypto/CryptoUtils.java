/*
 *   Copyright 2016 Author:Bestoa bestoapache@gmail.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package vproxy.base.util.crypto;

import vproxy.base.util.Utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class CryptoUtils {

    /**
     * Thanks go to Ola Bini for releasing this source on his blog.
     * The source was obtained from <a href="http://olabini.com/blog/tag/evp_bytestokey/">here</a> .
     */
    private static byte[][] EVP_BytesToKey(int key_len, int iv_len, MessageDigest md, byte[] salt, byte[] data, int count) {
        byte[][] both = new byte[2][];
        byte[] key = Utils.allocateByteArray(key_len);
        int key_ix = 0;
        byte[] iv = Utils.allocateByteArray(iv_len);
        int iv_ix = 0;
        both[0] = key;
        both[1] = iv;
        byte[] md_buf = null;
        int nkey = key_len;
        int niv = iv_len;
        int i = 0;
        if (data == null) {
            return both;
        }
        int addmd = 0;
        for (; ; ) {
            md.reset();
            if (addmd++ > 0) {
                md.update(md_buf);
            }
            md.update(data);
            if (null != salt) {
                md.update(salt, 0, 8);
            }
            md_buf = md.digest();
            for (i = 1; i < count; i++) {
                md.reset();
                md.update(md_buf);
                md_buf = md.digest();
            }
            i = 0;
            if (nkey > 0) {
                for (; ; ) {
                    if (nkey == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    key[key_ix++] = md_buf[i];
                    nkey--;
                    i++;
                }
            }
            if (niv > 0 && i != md_buf.length) {
                for (; ; ) {
                    if (niv == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    iv[iv_ix++] = md_buf[i];
                    niv--;
                    i++;
                }
            }
            if (nkey == 0 && niv == 0) {
                break;
            }
        }
        for (i = 0; i < md_buf.length; i++) {
            md_buf[i] = 0;
        }
        return both;
    }


    public static byte[] getKey(String password, int keyLen, int ivLen) {
        MessageDigest md = null;
        byte[] passwordBytes = null;
        byte[][] keyAndIV = null;

        try {
            md = MessageDigest.getInstance("MD5");
            passwordBytes = password.getBytes("ASCII");
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        //This key should equal EVP_BytesToKey with no salt and count = 1
        keyAndIV = EVP_BytesToKey(keyLen, ivLen, md, null, passwordBytes, 1);

        //Discard the iv.
        return keyAndIV[0];
    }

    public static byte[] randomBytes(int size) {
        byte[] bytes = Utils.allocateByteArray(size);
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    public static long md5ToPositiveLong(byte[] bytes) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] mdResult = md.digest(bytes);
        assert mdResult.length == 16;
        byte[] longArr = Utils.allocateByteArrayInitZero(8);
        for (int i = 0; i < 8; ++i) {
            longArr[i] = (byte) (mdResult[i] ^ mdResult[8 + i]);
        }
        long ret = 0;
        for (int i = 0; i < 8; ++i) {
            ret <<= 8;
            ret |= (longArr[i] & 0xff);
        }
        return Math.abs(ret);
    }
}
