package matou.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

public class Client {

    private final static Charset UTF8_charset = Charset.forName("UTF8");
    /*byte pour que le serveur sache qu'on envoie son pseudo*/
    private final static byte E_PSEUDO = 1;
    /* connection et accuse de reception de la connection au client */
    private final static byte CO_CLIENT_TO_CLIENT = 2;
    private final static byte ACK_CO_CLIENT = 3;
    /* envoie d'un message ou d'un fichier a un client */
    private final static byte M_CLIENT_TO_CLIENT = 4;
    private final static byte F_CLIENT_TO_CLIENT = 5;
    private final static byte D_LIST_CLIENT_CO = 7;
    private final static byte R_LIST_CLIENT_CO = 8;
    /* Concerne l'envoie et la reception */
    private final static byte M_ALL = 9;
    private final static byte R_PSEUDO = 10;
    private static final byte E_ADDR_SERV_CLIENT = 11;

    /*Le temps que doit attendre le programme entre deux actualisation de la liste*/
    private static final long ACTU_LIST_TIME_MILLIS = 1000 * 5;
    private final Scanner scan;
    // TODO
    private final List<Thread> tabThreadServer;
    private final List<Thread> tabThreadClient;
    boolean end = false;
    private String nickname;
    private HashMap<String, InetSocketAddress> mapClient;
    // Devra etre une map de <String, ServerSocketChannel>
    private HashMap<String, SocketChannel> friends;
    private SocketChannel socket;
    // Bricoler un paquet qui envoie l'adresse du serverSocketChannel
    private ServerSocketChannel serverSocketChannel;
    private int BUFFER_SIZE = 1024;
    private String messageAll = null;
    private String pseudoACK = null;
    private String pseudoConnect = null;
    private String fileName = null;
    private String userName = null;
    private String dest = null;
    private String messageToClient = null;
    private boolean canAccept = false;

    public Client(String host, int port) throws IOException {

        mapClient = new HashMap<>();
        friends = new HashMap<>();
        socket = SocketChannel.open();
        socket.connect(new InetSocketAddress(host, port));
        socket.configureBlocking(false);

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(serverSocketChannel.getLocalAddress());
        scan = new Scanner(System.in);

        // TODO
        tabThreadServer = new ArrayList<>();
        tabThreadClient = new ArrayList<>();
    }

    private static void usage() {
        System.out.println("java matou.client.Client localhost 7777");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            usage();
            return;
        }
        Client client = new Client(args[0], Integer.parseInt(args[1]));
        client.pseudoRegister();
        client.sendInfoServer();
        client.launch();
    }

    private void sendInfoServer() throws IOException {

        String serverBeforeStrip = serverSocketChannel.getLocalAddress().toString();
        ByteBuffer bInfoServer = UTF8_charset.encode(serverBeforeStrip.replace("/", ""));
        ByteBuffer bInfoServerToServer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + serverBeforeStrip.length() - 1);
        bInfoServerToServer.put(E_ADDR_SERV_CLIENT)
                .putInt(serverBeforeStrip.length() - 1)
                .put(bInfoServer);
        bInfoServerToServer.flip();

        socket.write(bInfoServerToServer);

    }

    private void pseudoRegister() throws IOException {
        while (!sendPseudo()) {
            System.out.println("Le pseudo est deja pris.");
        }
    }

    private boolean sendPseudo() throws IOException {
        System.out.println("Quel pseudo souhaitez vous avoir ?");

        if (scan.hasNextLine()) {
            nickname = scan.nextLine();
        }

        ByteBuffer bNickName = UTF8_charset.encode(nickname);
        ByteBuffer bNickNameToServer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + nickname.length());
        bNickNameToServer.put(E_PSEUDO)
                .putInt(nickname.length())
                .put(bNickName);
        bNickNameToServer.flip();

        socket.write(bNickNameToServer);

        ByteBuffer bReceive = ByteBuffer.allocate(BUFFER_SIZE);
        //lis des donnee, cherche le byte R_PSEUDO et jete le reste, s'il ne la pas trouver
        //dans tout le buffer il recommence a lire.
        do {
            while (socket.read(bReceive) == 0) ;
            bReceive.flip();
            while (bReceive.get() != R_PSEUDO && bReceive.hasRemaining()) ;
        } while (!bReceive.hasRemaining());
        int test = bReceive.getInt();
        return test == 0;
    }

    // Lit ce que le socketChannel recoit et le stock dans le buffer,
    // Si le buffer est trop petit , la taille est automatiquement augmenter
    // jusqu'a ce qu'il ne soit plus plein
    private ByteBuffer readAll(ByteBuffer bbIn, SocketChannel sc) throws IOException {
        while (sc.read(bbIn) != -1) {
            if (bbIn.position() < bbIn.limit()) {
                return bbIn;
            }
            bbIn.flip();
            ByteBuffer tempo = bbIn.duplicate();
            bbIn = ByteBuffer.allocateDirect(tempo.capacity() * 2);
            bbIn.put(tempo);
            if (!bbIn.hasRemaining()) {
                return bbIn;
            }
        }
        return null;
    }

    public void launch() {
        int size;
        Thread tRead = threadRead();
        tRead.start();
        long deb = System.currentTimeMillis();
        ByteBuffer buffByte = ByteBuffer.allocate(BUFFER_SIZE);
        ByteBuffer buffName;
        while (!Thread.interrupted()) {
            if (end) {
                break;
            }
            send();
            if (System.currentTimeMillis() - deb > ACTU_LIST_TIME_MILLIS) {
                demandeList();
                deb = System.currentTimeMillis();
            }

            try {
                if (null == (buffByte = readAll(buffByte, socket))) {
                    buffByte = ByteBuffer.allocate(BUFFER_SIZE);
                    continue;
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecté");
                endAllThread(); // TODO bien ajouter les threads créé dans la liste
                return;
            }
            buffByte.flip();
            while (buffByte.hasRemaining()) {
                Byte b = buffByte.get();
                switch (b) {
                    case CO_CLIENT_TO_CLIENT:
                        //se mettre en mode client
                        size = buffByte.getInt();
                        buffName = ByteBuffer.allocate(size);
                        for (int i = 0; i < size; i++) {
                            buffName.put(buffByte.get());
                        }
                        buffName.flip();
                        String user = UTF8_charset.decode(buffName).toString();
                        System.out.println(user + " souhaiterai se connecter avec vous,\npour ce faire, vous devez tapez /accept " + user);
                        canAccept = true;
                        break;
                    case R_LIST_CLIENT_CO:
                        mapClient = new HashMap<>();
                        //size + sizepseudo + stringpseudo + sizeadress + stringadress
                        size = buffByte.getInt();
                        while (size > 0) {
                            int sizePseudo = buffByte.getInt();
                            ByteBuffer buffClient = ByteBuffer.allocate(sizePseudo);
                            for (int i = 0; i < sizePseudo; i++) {
                                buffClient.put(buffByte.get());
                            }

                            int sizeSocket = buffByte.getInt();
                            ByteBuffer buffSocket = ByteBuffer.allocate(sizeSocket);
                            for (int i = 0; i < sizeSocket; i++) {
                                buffSocket.put(buffByte.get());
                            }
                            buffSocket.flip();
                            buffClient.flip();
                            addList(buffSocket, buffClient);
                            size--;
                        }
                        actualiseListFriend();
                        break;
                    case M_ALL:
                        size = buffByte.getInt();
                        ByteBuffer buffPseudo = ByteBuffer.allocate(size);
                        for (int i = 0; i < size; i++) {
                            buffPseudo.put(buffByte.get());
                        }

                        size = buffByte.getInt();
                        ByteBuffer buffMessenger = ByteBuffer.allocate(size);
                        for (int i = 0; i < size; i++) {
                            buffMessenger.put(buffByte.get());
                        }

                        buffPseudo.flip();
                        buffMessenger.flip();
                        System.out.println("[all] " + UTF8_charset.decode(buffPseudo) + " : " + UTF8_charset.decode(buffMessenger));
                        break;
                    default:
                        System.err.println("Error : Unkown code " + b);
                        break;
                }
            }
            buffByte.compact();
            try {
                Thread.sleep(1); // millisieste -> sinon mange tout le cpu
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //
        tRead.interrupt();

    }

    private void send() {
        if (messageAll != null) {
            sendMessageAll();
        }
        if (pseudoACK != null) {
            sendPseudoAck();
        }
        if (pseudoConnect != null) {
            sendPseudoConnect();
        }
        if (fileName != null && userName != null) {
            sendFile();
        }
        if (messageToClient != null && dest != null) {
            sendMessageToClient();
        }
    }

    private void sendFile() {
        //verifier que le client est dans la liste d'ami
        //lire dans filename
        //envoyer ce qui est lu en thread
        userName = null;
        fileName = null;
    }

    private void sendPseudoAck() {
        ByteBuffer buffSendACK = ByteBuffer.allocate(BUFFER_SIZE);
        buffSendACK.put(ACK_CO_CLIENT)
                .putInt(nickname.length())
                .put(UTF8_charset.encode(nickname));
        buffSendACK.flip();
        SocketChannel socketACK = null;
        try {
            socketACK = SocketChannel.open();
            socketACK.connect(mapClient.get(pseudoACK));
            socketACK.write(buffSendACK);
        } catch (IOException e) {
            System.err.println("Erreur lors de la connexion au client " + pseudoACK);
        }

        friends.put(pseudoACK, socketACK);
        System.out.println("Connexion accepté");
        System.out.println(pseudoACK + " est maintenant votre ami pour la session");
        clientClient(socketACK).start();
        pseudoACK = null;
    }

    private void sendPseudoConnect() {
        //envoyer demande a pseudo connect
        System.out.println("Connecting");
        ByteBuffer buffConnect = ByteBuffer.allocate(BUFFER_SIZE);
        buffConnect.put(CO_CLIENT_TO_CLIENT)
                .putInt(pseudoConnect.length())
                .put(UTF8_charset.encode(pseudoConnect))
                .putInt(nickname.length())
                .put(UTF8_charset.encode(nickname));
        buffConnect.flip();


        serverClient().start();
        try {
            socket.write(buffConnect);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie de la demande de connection, serveur deconnecté");
        }
        //se mettre en mode serveur
        pseudoConnect = null;
    }

    private void sendMessageToClient() {
        ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
        buffSend.put(M_CLIENT_TO_CLIENT)
                .putInt(messageToClient.length())
                .put(UTF8_charset.encode(messageToClient));
        buffSend.flip();
        friends.forEach((key, value) -> {
            if (key.equals(dest)) {
                try {
                    value.write(buffSend);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        messageToClient = null;
        dest = null;
    }

    private void sendMessageAll() {
        ByteBuffer buffMessage = UTF8_charset.encode(messageAll);
        ByteBuffer buffNickName = UTF8_charset.encode(nickname);

        ByteBuffer buffSendAll = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + nickname.length() + Integer.BYTES + messageAll.length());
        buffSendAll.put(M_ALL)
                .putInt(nickname.length())
                .put(buffNickName)
                .putInt(messageAll.length())
                .put(buffMessage);
        buffSendAll.flip();

        try {
            socket.write(buffSendAll);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie du message, serveur deconnecté");
        }
        messageAll = null;
    }

    private Thread serverClient() {
        return new Thread(() -> {
            SocketChannel s;
            try {
                System.out.println("Attend que le client se connecte");
                s = serverSocketChannel.accept();
                ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES);
                ByteBuffer buffName = ByteBuffer.allocate(BUFFER_SIZE);
                if (null == (buff = readAll(buff, s))) {
                    buff = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES);
                    // Securité si le client ferme la connection
//                    s.close();
//                    Thread.currentThread().interrupt();
                    return;
                }
                buff.flip();
                byte b = buff.get();
                if (b != ACK_CO_CLIENT) {
                    System.out.println("Erreur");
                }
                int size = buff.getInt();
                for (int i = 0; i < size; i++) {
                    buffName.put(buff.get());
                }
                buffName.flip();
                String name = UTF8_charset.decode(buffName).toString();
                friends.put(name, s);
                System.out.println("Connection with " + name + " done");
                readClient(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private Thread clientClient(SocketChannel s) {
        return new Thread(() -> {
            try {
                readClient(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void readClient(SocketChannel s) throws IOException {
//        System.out.println("readClient");
        ByteBuffer buffRead = ByteBuffer.allocate(BUFFER_SIZE);
        while (!Thread.interrupted()) {
            if ((buffRead = readAll(buffRead, s)) == null) {
                buffRead = ByteBuffer.allocate(BUFFER_SIZE);
                continue;
            }
            buffRead.flip();
            byte b = buffRead.get();
            switch (b) {
                case M_CLIENT_TO_CLIENT:
                    int size = buffRead.getInt();
                    ByteBuffer bMessage = ByteBuffer.allocate(size);
                    for (int i = 0; i < size; i++) {
                        bMessage.put(buffRead.get());
                    }
                    bMessage.flip();
                    friends.forEach((key, value) -> {
                        if (value.equals(s)) {
                            System.out.println("[private] from " + key + " : " + UTF8_charset.decode(bMessage));
                        }
                    });
                    break;
                case F_CLIENT_TO_CLIENT:
                    break;
            }
        }
    }

    private void demandeList() {
        ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES);
        buff.put(D_LIST_CLIENT_CO);
        buff.flip();
        try {
            socket.write(buff);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie de la demande liste, serveur deconnecté");
        }
    }
   /* private Thread threadClient(String pseudo) {
        return new Thread(() -> {
            //scanner.read
            //si il demande la connection il modifie requeteCo, sil demande un messageAll il modifie messageAll...
        });
    }*/

    private void addList(ByteBuffer buffSocket, ByteBuffer buffClient) {
        String socketChan = UTF8_charset.decode(buffSocket).toString();

        int splitIndex = socketChan.lastIndexOf(':');
        String host = socketChan.substring(0, splitIndex);
        int port = Integer.parseInt(socketChan.substring(splitIndex + 1));

        InetSocketAddress in = new InetSocketAddress(host, port);

        mapClient.put(UTF8_charset.decode(buffClient).toString(), in);
    }

    private void actualiseListFriend() {
//        friends.forEach((key, value) -> {
//            if (!mapClient.containsKey(key)) {
//                friends.remove(key);
//            }
//        });

        friends.keySet().removeIf(k -> !mapClient.containsKey(k));
    }

    private Thread threadRead() {

        return new Thread(() -> {
            listeCommande();
            System.out.println("Que souhaitez vous faire?");
            while (scan.hasNextLine()) {
                String line = scan.nextLine();
                String[] words = line.split(" ");
                ///////////////////////////////////////////////////////////
                if (words[0].equals("/all")) {
                    if (words.length < 2) {
                        System.err.println("empty message");
                    }
                    StringBuilder b = new StringBuilder();
                    String sep = "";
                    for (int i = 1; i < words.length; i++) {
                        b.append(sep);
                        b.append(words[i]);
                        sep = " ";
                    }
                    messageAll = b.toString();
                    System.out.println("[all] " + nickname + " : " + messageAll);
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/commandes")) {
                    listeCommande();
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/w")) {
                    if (words.length < 3) {
                        System.err.println("empty message");
                    }
                    dest = words[1];
                    if (friends.containsKey(dest)) {
                        StringBuilder b = new StringBuilder();
                        String sep = "";
                        for (int i = 2; i < words.length; i++) {
                            b.append(sep);
                            b.append(words[i]);
                            sep = " ";
                        }
                        messageToClient = b.toString();
                        System.out.println("[private] to " + dest + " : " + messageToClient);
                    } else {
                        if (mapClient.containsKey(dest)) {
                            System.out.println("Il faut vous connecter au client " + dest + " avant de lui envoyer des messages privé");
                        } else {
                            System.out.println("Le client " + dest + " n'existe pas");
                        }
                    }
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/accept")) {
                    if (canAccept) {
                        if (words.length < 2) {
                            System.err.println("Il faut donner un nom d'utilisateur sur lequel accepter");
                        } else if (words.length > 2) {
                            System.err.println("Trop d'argument , format /accept user");
                        } else {
                            pseudoACK = words[1];
                            canAccept = false;
                        }
                    } else {
                        System.err.println("Vous ne pouvez pas accepter de connexion si personne ne vous le demande");
                    }
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/connect")) {
                    if (!mapClient.isEmpty()) {
                        if (words.length < 2) {
                            System.err.println("Il faut donner un nom d'utilisateur sur lequel se connecter");
                        } else if (words.length > 2) {
                            System.err.println("Trop d'argument , format /connect user");
                        } else {
                            if (!friends.containsKey(words[1])) {
                                if (mapClient.containsKey(words[1])) {
                                    pseudoConnect = words[1];
                                } else {
                                    System.out.println("Le client " + words[1] + " n'existe pas");
                                }
                            } else {
                                System.out.println("Vous etes deja connecte au client " + words[1]);
                            }
                        }
                    } else {
                        System.out.println("Il n'y a personne de connecté sur le serveur autre que vous");
                    }
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/file")) {
                    if (words.length < 2) {
                        System.err.println("empty file name");
                    } else if (words.length > 2) {
                        System.err.println("too much argument");
                    } else {
                        userName = words[1];
                        fileName = words[2];
                    }
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/friends")) {
                    printFriends();
                } else if (words[0].equals("/clients")) {
                    printClients();
                }


                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/exit")) {
                    ByteBuffer b = ByteBuffer.allocate(Byte.BYTES);
                    byte byteSend = 6;
                    b.put(byteSend);
                    b.flip();
                    try {
                        socket.write(b);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    end = true;
                    scan.close();
                    endAllThread();
                    break;
                }
                ///////////////////////////////////////////////////////////
                else {
                    System.err.println("Commande inconnu : " + words[0]);
                    listeCommande();
                }
            }
        });
    }

    private void printFriends() {
        if (friends.isEmpty()) {
            System.out.println("Désolé, vous n'avez pas d'amis connecté");
            return;
        }
        System.out.println("Liste des amis connecté : ");
        friends.keySet().stream().forEach(System.out::println);
    }

    private void printClients() {
        if (mapClient.size() == 1) {
            System.out.println("Vous êtes seul sur le serveur");
            return;
        }
        System.out.println("Liste des clients connecté : ");
        mapClient.keySet().stream().forEach(System.out::println);
    }


    private void endAllThread() {
        System.out.println("Killing all living threads");
        tabThreadServer.forEach(Thread::interrupt);
        tabThreadClient.forEach(Thread::interrupt);
        System.out.println("Exiting program");
    }

    private void listeCommande() {
        System.out.println("voici les commandes utilisateur :\n"
                + "/commandes pour lister les commande\n"
                + "/w pseudo message pour envoyer un message a pseudo\n"
                + "/all monMessage pour envoyer un message a tout les clients\n"
                + "/connect pseudo pour demander a vous connecter au client nomme pseudo\n"
                + "/accept pseudo pour accepter la connection au client nomme pseudo\n"
                + "/file pseudo nomDuFichier pour envoyer un fichier a pseudo (non implementé actuellement)\n" // TODO enlever fin de message une fois implementé
                + "/friends affiche la liste des personnes avec qui on est connecté\n"
                + "/clients affiche la liste des clients connecté au serveur\n"
                + "/exit pour quittez la messagerie");
    }
}
