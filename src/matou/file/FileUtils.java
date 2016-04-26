package matou.file;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Project :Matou
 * Created by Narex on 12/04/2016.
 */
public class FileUtils {

    private final static int MAX_SIZE = 50000000; // 50Mo


    /**
     * Lis le contenu du fichier en entree et stocke tout dans un bytebuffer qu'on renvoie
     * @param name nom du fichier en entree
     * @return bytebuffer a envoyee
     * @throws IOException Si une IOException se produit
     */
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
            System.out.println("Le fichier " + name + " est trop gros, taille maximal : " + MAX_SIZE / 1000000 + "Mo");
            return null;
        }
        byte[] buffer = Files.readAllBytes(path);
        ByteBuffer endBuffer = ByteBuffer.allocate(size.intValue());
        endBuffer.put(buffer);
        endBuffer.flip();
        return endBuffer;
    }

    /**
     * Lis le contenu du byteBuffer en entree et ecrit dans un fichier ce contenu
     * @param byteBuffer buffer de reception des donnees
     * @param filename   nom du fichier voulu en sortie
     * @return un boolean qui indique si on a bien tout ecrit ou non
     */
    public static boolean readInBufferAndWriteInFile(ByteBuffer byteBuffer, String filename) {

        Path path = Paths.get(filename);
        int i = 0;
        String cpy = filename;
        while (Files.exists(path)) {
            System.out.println("Le fichier " + cpy + " existe deja");
            cpy = filename + "_" + i;
            path = Paths.get(cpy);
            ++i;
        }
        System.out.println("Ajout d'une copie avec pour nom " + cpy);
        byte[] buff = byteBuffer.array();
        try {
            Files.write(path, buff);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
