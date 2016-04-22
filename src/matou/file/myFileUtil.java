package matou.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Project :Matou
 * Created by Narex on 12/04/2016.
 */
public class myFileUtil {

    private final int MAX_SIZE = 1000; // TODO: 22/04/2016 -> change that

    public myFileUtil() {

    }


    public static ByteBuffer readAndStoreInBuffer(String name) throws IOException {
        Path path = Paths.get(name);
        if (!Files.exists(path)) {
            System.out.println("Le fichier " + name + " n'existe pas");
            return null;
        }
        if (!Files.isRegularFile(path)) {
            System.out.println("Le chemin specifier " + name + " n'est pas un fichier");
            return null;
        }
        Long size = Files.size(path);
        if (size > MAX_SIZE) {
            System.out.println("Le fichier " + name + " est trop gros");
            return null;
        }
        byte[] buffer = new byte[4096];
        InputStream inputStream = Files.newInputStream(path);
        int nbRead = 0;
        ByteBuffer endBuffer = ByteBuffer.allocate(size.intValue());
        endBuffer.putInt(size.intValue());
        while ((nbRead = inputStream.read(buffer)) != -1) {
            endBuffer.put(buffer);
        }
        return endBuffer;
    }

}
