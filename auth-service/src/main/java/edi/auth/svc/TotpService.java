package edi.auth.svc;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.apache.commons.codec.binary.Base32;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * TOTP segun RFC 6238 (HMAC-SHA1, 6 digitos, pasos de 30 s), compatible con Google y Microsoft
 * Authenticator.
 */
@Service
public class TotpService {

    static final int PERIOD_SECONDS = 30;
    private static final int DIGITS_MOD = 1_000_000;
    private static final int QR_SIZE = 240;

    private final SecureRandom random = new SecureRandom();
    private final String issuer;

    public TotpService(@Value("${auth.mfa.issuer-label:AS2 Platform}") String issuer) {
        this.issuer = issuer;
    }

    /** Secreto de 160 bits en Base32, sin relleno. */
    public String generateBase32Secret() {
        byte[] buff = new byte[20];
        random.nextBytes(buff);
        return new Base32().encodeToString(buff).replace("=", "");
    }

    /** URI {@code otpauth://} estandar; la app autenticadora la lee desde el QR. */
    public String buildOtpAuthUrl(String username, String base32Secret) {
        String label = url(issuer) + ":" + url(username);
        return "otpauth://totp/" + label + "?secret=" + url(base32Secret) + "&issuer=" + url(issuer)
                + "&digits=6&period=" + PERIOD_SECONDS;
    }

    /** Verifica el codigo admitiendo un paso de desfase de reloj (-1..+1). */
    public boolean verifyCode(String base32Secret, String code) {
        return verifyCode(base32Secret, code, Instant.now());
    }

    boolean verifyCode(String base32Secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{6}")) {
            return false;
        }
        int expected = Integer.parseInt(code);
        long step = now.getEpochSecond() / PERIOD_SECONDS;
        for (long s = step - 1; s <= step + 1; s++) {
            if (totp(base32Secret, s) == expected) {
                return true;
            }
        }
        return false;
    }

    /** Codigo vigente en un instante dado, con ceros a la izquierda. */
    public String codeAt(String base32Secret, Instant instant) {
        return String.format("%06d", totp(base32Secret, instant.getEpochSecond() / PERIOD_SECONDS));
    }

    /*
     * HMAC-SHA1 es el algoritmo de la RFC 6238 y el unico que aceptan todas las apps autenticadoras.
     * La debilidad de SHA-1 (colisiones) no afecta a HMAC, que es como se usa aqui.
     */
    @SuppressWarnings("java:S4790")
    private static int totp(String base32Secret, long timeStep) {
        try {
            byte[] key = new Base32().decode(base32Secret);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(timeStep).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return binary % DIGITS_MOD;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error calculando TOTP", e);
        }
    }

    /** QR en PNG (Base64) de la URI otpauth. */
    public String qrPngBase64(String otpauthUrl) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(otpauthUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            BufferedImage img = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < QR_SIZE; x++) {
                for (int y = 0; y < QR_SIZE; y++) {
                    img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException e) {
            throw new IllegalStateException("Error generando el QR", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
