package vproxy.redis.application;

import vproxy.util.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Function;

public class RESPApplicationConfig {
    static final Function<byte[], byte[]> hashCrypto;

    static {
        //noinspection ConstantConditions
        do {
            // use do-while(false) because static block cannot `return`
            // use break instead
            MessageDigest messageDigest;
            try {
                messageDigest = MessageDigest.getInstance("SHA-512");
            } catch (NoSuchAlgorithmException e) {
                // should not happen, but if happens
                // set hashCrypto to `not doing anything`
                hashCrypto = b -> b;
                Logger.shouldNotHappen("no SHA-512");
                break;
            }
            hashCrypto = bytes -> {
                messageDigest.update(bytes);
                return messageDigest.digest();
            };
        } while (false);
    }

    byte[] password;

    // the password byte array will be cleared
    // after doing hash
    public RESPApplicationConfig setPassword(byte[] password) {
        if (password == null) {
            this.password = null; // remove the password
            return this;
        }
        this.password = hashCrypto.apply(password);
        for (int i = 0; i < password.length; ++i) {
            password[i] = 0; // fill with 0
        }
        return this;
    }
}
