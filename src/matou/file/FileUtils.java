package matou.file;

/**
 * Project :Matou
 * Created by Narex on 12/04/2016.
 */
public class FileUtils {

    private String fileName;
    private boolean read = false;
    private boolean write = false;

    public FileUtils(String fileName, boolean read, boolean write) {
        this.fileName = fileName;
        this.read = read;
        this.write = write;
    }

    public void readInfile() {
        if (read) {

        } else {
            System.err.println("Vous n'avez pas demandé à avoir le fichier en lecture");
        }

    }

    public void writeInfile() {
        if (write) {

        } else {
            System.err.println("Vous n'avez pas demandé à avoir le fichier en écriture");
        }

    }
}
