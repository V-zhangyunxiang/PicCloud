package poseidon.crypt;

import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The Cryptor class provides methods for encrypting and decrypting strings.
 * It also handles the loading of native libraries required for cryptographic operations.
 */
@SuppressWarnings("SpellCheckingInspection")
public final class Cryptor {

    private Cryptor() {
        // Prevent instantiation
    }

    static {
        loadNativeLibrary();
    }

    @SuppressWarnings("UnsafeDynamicallyLoadedCode")
    private static void loadNativeLibrary() {
        String os = System.getProperty("os.name").toLowerCase();
        final String libPath;
        if (SystemUtils.IS_OS_MAC) {
            libPath = "/lib/libPoseidonCypt.dylib";
        } else if (SystemUtils.IS_OS_LINUX) {
            libPath = "/lib/libPoseidonCypt.so";
        } else if (SystemUtils.IS_OS_WINDOWS) {
            libPath = "/lib/PoseidonCypt.dll";
        } else {
            throw new RuntimeException("Unsupported OS: " + os);
        }

        try (InputStream in = Cryptor.class.getResourceAsStream(libPath)) {
            if (in == null)
                throw new RuntimeException("Library not found: " + libPath + " in resource.");

            File tempFile = File.createTempFile("poseidon_cryptor_lib", getFileExtension(libPath));
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            }

            System.load(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load native library", e);
        }
    }

    private static String getFileExtension(String path) {
        return path.substring(path.lastIndexOf("."));
    }


    /**
     * Encrypts the given input string.
     *
     * @param input the string to be encrypted
     * @return the encrypted string
     */
    public static native String encrypt(String input);

    /**
     * Decrypts the given input string.
     *
     * @param input the string to be decrypted
     * @return the decrypted string
     */
    public static native String decrypt(String input);
}
