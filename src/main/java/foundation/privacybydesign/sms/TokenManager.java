package foundation.privacybydesign.sms;

import foundation.privacybydesign.common.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;

/**
 * Generate and verify tokens sent in the SMS message.
 * A token is locked to an IP address and a phone number.
 */
public class TokenManager {
    static private TokenManager instance;
    private static Logger logger = LoggerFactory.getLogger(TokenManager.class);

    // Map to store sent tokens.
    // Format: {"phone": TokenRequest}
    private HashMap<String, TokenRequest> tokenMap;
    private SecureRandom random;

    public TokenManager() {
        tokenMap = new HashMap<>();
        random = new SecureRandom();
    }

    public static TokenManager getInstance() {
        if (instance == null) {
            instance = new TokenManager();
        }
        return instance;
    }

    public String generate(String phone, String addr) {
        // https://stackoverflow.com/a/41156/559350
        // There are 30 bits. Using 32 possible values per char means
        // every char consumes exactly 5 bits, thus a token is 6 bytes.
        String token = new BigInteger(30, random).toString(32).toUpperCase();
        // ...but when the BigInteger is < 2**25, the first char is 0 and is
        // thus ignored. Add it here at the start (humans count in big endian).
        while (token.length() < 6) {
            token = "0" + token;
        }
        // With a toString of 32, there are 4 alphanumeric chars left: WXYZ.
        // Replace easy to confuse chars with those chars.
        token = token.replace('0', 'W');
        token = token.replace('O', 'X');
        token = token.replace('1', 'Y');
        token = token.replace('I', 'Z');
        tokenMap.put(phone, new TokenRequest(token, addr));
        return token;
    }

    public boolean verify(String phone, String addr, String token) {
        TokenRequest tr = tokenMap.get(phone);
        SMSConfiguration conf = SMSConfiguration.getInstance();
        if (System.currentTimeMillis() - tr.created > conf.getSMSTokenValidity()*1000) {
            logger.error("Token {} expired", token);
            // TODO: report this error back to the user.
            return false;
        }
        if (!CryptoUtil.isEqualsConstantTime(tr.addr.toCharArray(), addr.toCharArray())) {
            logger.error("Token {} verified by the wrong IP address", token);
            // Lock to an IP address.
            // Constant-time comparison isn't really required here, but let's
            // do it just to be sure.
            return false;
        }
        if (!CryptoUtil.isEqualsConstantTime(tr.token.toCharArray(), token.toCharArray())) {
            tr.tries++;
            logger.error("Token {} is wrong", token);
            return false;
        }
        if (tr.tries > 3) {
            logger.error("Token {} was tried to validate too often", token);
            // User may try at most 3 times, it shouldn't be that hard.
            // TODO: report this error back to the user.
            return false;
        }
        tokenMap.remove(phone);
        return true;
    }
}

class TokenRequest {
    public String token;
    public long created;
    public String addr;
    public int tries;

    public TokenRequest(String token, String addr) {
        this.token = token;
        this.addr = addr;
        created = System.currentTimeMillis();
        tries = 0;
    }
}