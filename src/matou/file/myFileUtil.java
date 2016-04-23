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
public class myFileUtil {

    private final static int MAX_SIZE = 1000; // TODO: 22/04/2016 -> change that
    private final int BUFF_SIZE = 4096;

    public myFileUtil() {

    }

    /**
     * @param name nom du fichier en entree
     * @return bytebuffer à envoyee
     * @throws IOException
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
            System.out.println("Le fichier " + name + " est trop gros");
            return null;
        }
//        byte[] buffer = new byte[BUFF_SIZE];
//        InputStream inputStream = Files.newInputStream(path);
//        int nbRead = 0;
//
//
//        ByteBuffer endBuffer = ByteBuffer.allocate(size.intValue());
//        endBuffer.putInt(size.intValue());
//        while ((inputStream.read(buffer)) != -1) {
//            endBuffer.put(buffer);
//        }
//        return endBuffer;
        byte[] buffer = Files.readAllBytes(path);
        ByteBuffer endBuffer = ByteBuffer.allocate(size.intValue());

        ///////// TEST ////////


        ///////////////////////

        endBuffer.put(buffer);
        return endBuffer;
    }

    /**
     * @param byteBuffer buffer de reception des donnees
     * @param filename   nom du fichier voulu en sortie
     * @return un boolean qui indique si on a bien tout ecrit ou non
     */
    public static boolean readInBufferAndWriteInFile(ByteBuffer byteBuffer, String filename) {

        Path path = Paths.get(filename);
        if (Files.exists(path)) {
            System.out.println("Le fichier " + filename + " existe deja");
            return false;
        }
        byte[] buff = byteBuffer.array();
        try {
            Files.write(path, buff);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        OutputStream outputStream = Files.newOutputStream(path);
//        while (byteBuffer.hasRemaining()) {
//
//            byteBuffer.get(buffer);
//
//            outputStream.write(buffer);
//        }
        return true;
    }

    // Code a ecrire avant fonction read in Buffer
    // Donner le buffer "tempo" au

    /**
     * @param in   Buffer dans lequel on lit
     * @param size Taille de la zone que l'on veut lire
     * @return Le nouveau buffer de taille "size" et contenant une partie du buffer In
     */
    public ByteBuffer copyPartialBuffer(ByteBuffer in, int size) {
        ByteBuffer tempo = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            tempo.put(in.get());
        }
        tempo.flip();
        return tempo;
    }

}
