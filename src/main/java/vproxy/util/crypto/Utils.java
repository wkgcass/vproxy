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
package vproxy.util.crypto;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class Utils {

    /**
     * Thanks go to Ola Bini for releasing this source on his blog.
     * The source was obtained from <a href="http://olabini.com/blog/tag/evp_bytestokey/">here</a> .
     */
    private static byte[][] EVP_BytesToKey(int key_len, int iv_len, MessageDigest md, byte[] salt, byte[] data, int count) {
        byte[][] both = new byte[2][];
        byte[] key = new byte[key_len];
        int key_ix = 0;
        byte[] iv = new byte[iv_len];
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
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static boolean printBytes(byte[] array) {
        final int bytesPerLine = 36;
        int lastLine = array.length % bytesPerLine;
        if (lastLine == 0) {
            lastLine = bytesPerLine;
        }
        int lines = array.length / bytesPerLine + (lastLine != bytesPerLine ? 1 : 0);
        byte[][] linesArray = new byte[lines][];
        for (int i = 0; i < linesArray.length - 1; ++i) {
            linesArray[i] = new byte[bytesPerLine];
        }
        linesArray[linesArray.length - 1] = new byte[lastLine];

        for (int i = 0; i < array.length; ++i) {
            int idx0 = i / bytesPerLine;
            int idx1 = i % bytesPerLine;
            linesArray[idx0][idx1] = array[i];
        }

        for (int idx = 0; idx < linesArray.length; idx++) {
            byte[] line = linesArray[idx];
            System.out.print(bytesToHex(line));
            System.out.print("    ");
            if (idx == linesArray.length - 1) {
                for (int i = 0; i < 2 * (bytesPerLine - lastLine); ++i) {
                    System.out.print(" ");
                }
            }
            char[] cs = new char[line.length];
            for (int i = 0; i < line.length; ++i) {
                int b = line[i];
                if (b < 0) {
                    b += 256;
                }
                if (b < 32 || b > 126) {
                    cs[i] = '.';
                } else {
                    cs[i] = (char) b;
                }
            }
            System.out.println(new String(cs));
        }

        return true;
    }
}
