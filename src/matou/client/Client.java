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
    /*deconnection du client*/
    private final static byte DC_PSEUDO = 6;
    /*envoie et reception de la liste des clients*/
    private final static byte D_LIST_CLIENT_CO = 7;
    private final static byte R_LIST_CLIENT_CO = 8;
    /* Concerne l'envoie et la reception */
    private final static byte M_ALL = 9;
    /*reception pseudo*/
    private final static byte R_PSEUDO = 10;
    /*envoie adresse du serveur du client au serveur principal*/
    private final static byte E_ADDR_SERV_CLIENT = 11;

    /*Le temps que doit attendre le programme entre deux actualisation de la liste*/
    private final static long ACTU_LIST_TIME_MILLIS = 1000 * 5;

    private final static int BUFFER_SIZE = 1024;

    private final Scanner scan;

    private final List<Thread> tabThreadServer;
    private final List<Thread> tabThreadClient;
    private final HashMap<String, SocketChannel> friends;
    private final SocketChannel socket;
    private final ServerSocketChannel serverSocketChannel;
    private String nickname;
    /*map des client et des amis, mapClient ne peux pas etre final
     * car il est recrer a chaque fois que l'on actualise la liste*/
    private HashMap<String, InetSocketAddress> mapClient;
    private String messageAll = null;
    private String pseudoACK = null;
    private String pseudoConnect = null;
    private String fileName = null;
    private String userName = null;
    private String dest = null;
    private String messageToClient = null;

    private boolean canAccept = false;
    private boolean end = false;

    public Client(String host, int port) throws IOException {

        mapClient = new HashMap<>();
        friends = new HashMap<>();
        socket = SocketChannel.open();
        socket.connect(new InetSocketAddress(host, port));
        socket.configureBlocking(false);

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(serverSocketChannel.getLocalAddress());
        scan = new Scanner(System.in);

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
        client.launch();
    }

    public void launch() throws IOException {
        pseudoRegister();
        sendInfoServer();
        Thread tRead = threadRead();
        tRead.start();
        //TODO insert tRead dans liste des threads
        receiveServer();
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
            String tmp = scan.nextLine();
            ByteBuffer bNickName = UTF8_charset.encode(tmp);
            ByteBuffer bNickNameToServer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + tmp.length());
            bNickNameToServer.put(E_PSEUDO)
                    .putInt(tmp.length())
                    .put(bNickName);
            bNickNameToServer.flip();
            socket.write(bNickNameToServer);
            //lis des donnee, cherche le byte R_PSEUDO et jete le reste, s'il ne la pas trouver
            //dans tout le buffer il recommence a lire.
            ByteBuffer bReceive = ByteBuffer.allocate(BUFFER_SIZE);
            do {
                while (socket.read(bReceive) == 0) ;
                bReceive.flip();
                while (bReceive.get() != R_PSEUDO && bReceive.hasRemaining()) ;
            } while (!bReceive.hasRemaining());
            int test = bReceive.getInt();
            if (test == 1) {
                nickname = tmp;
                return true;
            } else if (test == 2) {
                System.out.println("Vous avez déjà choisi votre pseudo");
                return false;
            } else {

                return false;
            }
        }
        throw new IllegalStateException("nickname never send");
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

    public void receiveServer() {
        long deb = System.currentTimeMillis();
        ByteBuffer buffByte = ByteBuffer.allocate(BUFFER_SIZE);
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
                System.err.println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecter");
                exitClient();
                return;
            }
            buffByte.flip();
            while (buffByte.hasRemaining()) {
                Byte b = buffByte.get();
                switch (b) {
                    case CO_CLIENT_TO_CLIENT:
                        decodeCoClient(buffByte);
                        break;
                    case R_LIST_CLIENT_CO:
                        decodeRList(buffByte);
                        break;
                    case M_ALL:
                        decodeMessageAll(buffByte);
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
    }

    private void decodeMessageAll(ByteBuffer buffByte) {
        int size;
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
    }

    private void decodeCoClient(ByteBuffer buffByte) {
        int size;
        ByteBuffer buffName;
        size = buffByte.getInt();
        buffName = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            buffName.put(buffByte.get());
        }
        buffName.flip();
        String user = UTF8_charset.decode(buffName).toString();
        System.out.println(user + " souhaiterai se connecter avec vous,\npour ce faire, vous devez tapez /accept " + user);
        canAccept = true;
    }

    private void decodeRList(ByteBuffer buffByte) {
        int size;
        mapClient = new HashMap<>();
        for (size = buffByte.getInt(); size > 0; size--) {
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
            String socketClient = UTF8_charset.decode(buffSocket).toString();
            String nameClient = UTF8_charset.decode(buffClient).toString();
            addList(socketClient, nameClient);
        }
        actualiseListFriend();
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
            System.err.println("Erreur lors de l'envoie du message, serveur deconnecter");
            exitClient();
        }
        messageAll = null;
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
            exitClient();
        }

        friends.put(pseudoACK, socketACK);
        System.out.println("Connexion accepter");
        System.out.println("Vous etes maintenant connecter avec " + pseudoACK);
        Thread tmp = clientClient(socketACK);
        //tabThreadClient.add(tmp);
        tmp.start();
        pseudoACK = null;
    }

    private void sendPseudoConnect() {
        //envoyer demande a pseudo connect
        System.out.println("Demande de connexion envoyer");
        ByteBuffer buffConnect = ByteBuffer.allocate(BUFFER_SIZE);
        buffConnect.put(CO_CLIENT_TO_CLIENT)
                .putInt(pseudoConnect.length())
                .put(UTF8_charset.encode(pseudoConnect))
                .putInt(nickname.length())
                .put(UTF8_charset.encode(nickname));
        buffConnect.flip();

        Thread tmp = serverClient();
        //tabThreadClient.add(tmp);
        tmp.start();
        try {
            socket.write(buffConnect);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie de la demande de connection, serveur deconnecter");
            exitClient();
        }
        pseudoConnect = null;
    }

    private void sendFile() {
        //verifier que le client est dans la liste d'ami
        //lire dans filename
        //envoyer ce qui est lu en thread
        userName = null;
        fileName = null;
    }

    private void sendMessageToClient() {
        ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
        buffSend.put(M_CLIENT_TO_CLIENT)
                .putInt(messageToClient.length())
                .put(UTF8_charset.encode(messageToClient));
        buffSend.flip();
        friends.forEach((name, socketFriend) -> {
            if (name.equals(dest)) {
                try {
                    socketFriend.write(buffSend);
                } catch (IOException e) {
                    System.err.println("Erreur lors de l'envoie du message au client " + dest);
                }
            }
        });
        messageToClient = null;
        dest = null;
    }

    private Thread serverClient() {
        return new Thread(() -> {
            SocketChannel s;
            try {
                System.out.println("Attend que le client se connecte");//TODO parler de l'acceptation de l'autre client qui est necessaire
                s = serverSocketChannel.accept();
                ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES);
                ByteBuffer buffName = ByteBuffer.allocate(BUFFER_SIZE);
                if (null == (buff = readAll(buff, s))) {
                    s.close();
                    Thread.currentThread().interrupt();
                    return;
                }
                buff.flip();
                byte b = buff.get();
                if (b != ACK_CO_CLIENT) {
                    System.err.println("Error : receive wrong byte");
                    return;
                }
                int size = buff.getInt();
                for (int i = 0; i < size; i++) {
                    buffName.put(buff.get());
                }
                buffName.flip();
                String name = UTF8_charset.decode(buffName).toString();
                friends.put(name, s);
                System.out.println("Connexion avec " + name + " etablit");
                readClient(s);
            } catch (IOException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private Thread clientClient(SocketChannel s) {
        return new Thread(() -> {
            try {
                readClient(s);
            } catch (Exception e) {
            }
        });
    }

    private void readClient(SocketChannel s) throws IOException {
        ByteBuffer buffRead = ByteBuffer.allocate(BUFFER_SIZE);
        while (!Thread.interrupted()) {
            if ((buffRead = readAll(buffRead, s)) == null) {
                friends.forEach((name, socketFriend) -> {
                    if (socketFriend.equals(s)) {
                        System.out.println("client " + name + " s'est deconnecter");
                    }
                });
                Thread.currentThread().interrupt();
                return;
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
                    friends.forEach((name, socketFriend) -> {
                        if (socketFriend.equals(s)) {
                            System.out.println("[private] from " + name + " : " + UTF8_charset.decode(bMessage));
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
            System.err.println("Erreur lors de l'envoie de la demande liste, serveur deconnecter");
        }
    }

    private void addList(String socketC, String nameClient) {

        int splitIndex = socketC.lastIndexOf(':');
        String host = socketC.substring(0, splitIndex);
        int port = Integer.parseInt(socketC.substring(splitIndex + 1));

        InetSocketAddress in = new InetSocketAddress(host, port);

        mapClient.put(nameClient, in);
    }

    private void actualiseListFriend() {
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
                        System.err.println("Vous n'avez pas mis de message");
                    }
                    messageAll = constructMessage(1, words);
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/commandes")) {
                    listeCommande();
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/w")) {
                    if (words.length < 3) {
                        System.err.println("Vous n'avez pas mis de message");
                    }
                    dest = words[1];
                    if (friends.containsKey(dest)) {
                        messageToClient = constructMessage(2, words);
                    } else {
                        if (mapClient.containsKey(dest)) {
                            System.out.println("Il faut vous connecter au client " + dest + " avant de lui envoyer des messages privee");
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
                        System.out.println("Il n'y a personne de connecter sur le serveur autre que vous");
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
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/clients")) {
                    printClients();
                }
                ///////////////////////////////////////////////////////////
                else if (words[0].equals("/exit")) {
                    exitClient();
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

    private void exitClient() {
        ByteBuffer b = ByteBuffer.allocate(Byte.BYTES);
        b.put(DC_PSEUDO);
        b.flip();
        try {
            socket.write(b);
        } catch (IOException e) {
            System.err.println("Erreur lors de la deconnection, serveur deconnecter");
        }
        end = true;
//        scan.close();
        endAllThread();
    }

    private String constructMessage(int debIter, String[] words) {
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (int i = debIter; i < words.length; i++) {
            b.append(sep);
            b.append(words[i]);
            sep = " ";
        }
        return b.toString();
    }

    private void printFriends() {
        if (friends.isEmpty()) {
            System.out.println("Desoler, vous n'avez pas d'amis connecter");
            return;
        }
        System.out.println("Liste des amis connecter : ");
        friends.keySet().stream().forEach(System.out::println);
    }

    private void printClients() {
        if (mapClient.size() == 1) {
            System.out.println("Vous etes seul sur le serveur :'(");
            return;
        }
        System.out.println("Liste des clients connecter : ");
        mapClient.keySet().stream().forEach(System.out::println);
    }


    private void endAllThread() {
        //System.out.println("Killing all living threads");
        tabThreadServer.forEach(Thread::interrupt);
        tabThreadClient.forEach(Thread::interrupt);
        //System.out.println("Exiting program");
    }

    private void listeCommande() {
        System.out.println("Voici les commandes utilisateur :\n"
                + "/commandes pour lister les commande\n"
                + "/w pseudo message pour envoyer un message a pseudo\n"
                + "/all monMessage pour envoyer un message a tout les clients\n"
                + "/connect pseudo pour demander a vous connecter au client nomme pseudo\n"
                + "/accept pseudo pour accepter la connection au client nomme pseudo\n"
                + "/file pseudo nomDuFichier pour envoyer un fichier a pseudo (non implementer actuellement)\n" // TODO enlever fin de message une fois implemente
                + "/friends affiche la liste des personnes avec qui on est connecter\n"
                + "/clients affiche la liste des clients connecter au serveur\n"
                + "/exit pour quittez la messagerie");
    }
}
