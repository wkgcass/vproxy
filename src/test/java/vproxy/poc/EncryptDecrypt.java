package vproxy.poc;

import vproxy.util.RingBuffer;
import vproxy.util.crypto.Aes256Key;
import vproxy.util.crypto.Utils;
import vproxy.util.nio.ByteArrayChannel;
import vproxy.util.ringbuffer.DecryptIVInDataUnwrapRingBuffer;
import vproxy.util.ringbuffer.EncryptIVInDataWrapRingBuffer;

import java.util.Arrays;

public class EncryptDecrypt {
    public static void main(String[] args) throws Exception {
        String[][] toDecryptArray = {
            {
                "d0f80c51d2b58fda1869b152dc315283",
                "a4e1fce9ab6c9477e626113a77ebd29e206c5c778055035e4cad882a73706ee0feb2a79b5b6b895a0f05f81b4d82ab93b5a2810c7d4945646bac7055f69c2bcc70dc8956c6db577a05e7789d8b2a6c86ea8526483dbe986e156eaecb3cdeea82760073db37cd1282bd99253c8fa84190b42b7c25938bbf97df3df3ac7f7c6c76e3778237dc7f3a92e68ca993e895701c26454300c362759d8bfd7a784177ba45a8a3022cafbad907c1291bfa9a7f09f2902b5538f339ed8f148842a75f66a2e53b12245f8d435cb0df3b0184e84a33e564a4b4f3e10c0c8e1976199d7b62041c0175324fc84b48c88c735fa53f590ad8a95afec1b0032886a993825d5b84e7cc3e941c9f13ee06d57af1d95049d13eb2b530a53e35912302f636b7e2abce67a56ae4a7c17d75dd48d9822852f06556c15b7170912b5b1354a65704b8a8342812227fefa636379d466ea006d92ac42f88bb6ae73ed31aa0b7c6147c16178071bcd748b528d5e9176ea64fd0c842e233fb934a08cdfa21341fba3e1048dd675f154d17bfa3d260b015185297cdcf8e8c3c8d0cbbd982c3d9f0c9ee8e83c1cdf6b2cc45dc59617ca230a2fd08775d65db4fda2528e1444649167b21af46803a71fe0976300bec4525db60a6853c1ac6b830c57a63e276e73625e55db468f8dd6889f04d31e235bbe3c1a8fdc35868d998688db44b53425e29d40ba8c28e15395c41ddf4d53743468eb1902602d5f302252ea959c3a0080b"
            },
            {
                "1ed22be471e26cfa42330b5f6a290df8",
                "85a0013e11b3bb64f0cf222f37865d875b14848a0dbe186f092341c1d12a0bcf60d5ee1c143b97ad513f63a7cb06daa54c1426a0b09c4bee2c065321a661eaf037c5fa74fff72a8972832240bd47479edac386d44d5a1d589c94d82923c3"
            },
        };

        for (String[] toDecrypt : toDecryptArray) {
            var key = new Aes256Key("123456");

            byte[] bytes = hexStringToByteArray(toDecrypt[0] + toDecrypt[1]);

            // test decrypt
            for (int step : Arrays.asList(13, 29, 16384)) {

                var plain = RingBuffer.allocate(2048);
                var decryptBuf = new DecryptIVInDataUnwrapRingBuffer(plain, key);

                for (int off = 0; off < bytes.length; off += step) {
                    int end = off + step;
                    if (end >= bytes.length) {
                        end = bytes.length;
                    }
                    decryptBuf.storeBytesFrom(ByteArrayChannel.from(bytes, off, end, 0));
                    int len = plain.used();
                    if (len == 0) {
                        if (end > key.ivLen()) {
                            throw new Exception("len == 0 but the iv is already read. current end = " + end);
                        }
                        continue;
                    }
                    byte[] arr = new byte[len];
                    plain.writeTo(ByteArrayChannel.fromEmpty(arr));

                    Utils.printBytes(arr);
                }
                System.out.println("- - - - - - - -");
            }
            System.out.println("------------");

            // test encrypt
            var plain = RingBuffer.allocate(2048);
            var decryptBuf = new DecryptIVInDataUnwrapRingBuffer(plain, key);
            decryptBuf.storeBytesFrom(ByteArrayChannel.fromFull(bytes));

            var plain2 = RingBuffer.allocate(2048);
            var encryptBuf = new EncryptIVInDataWrapRingBuffer(plain2, key, hexStringToByteArray(toDecrypt[0]));
            plain2.storeBytesFrom(ByteArrayChannel.fromFull(plain.getBytes()));
            byte[] encryptedArray = new byte[encryptBuf.used()];
            encryptBuf.writeTo(ByteArrayChannel.fromEmpty(encryptedArray));

            Utils.printBytes(encryptedArray);
            if (!Arrays.equals(encryptedArray, hexStringToByteArray(toDecrypt[0] + toDecrypt[1]))) {
                throw new Exception("encrypting failed");
            }

            System.out.println("==============");
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
