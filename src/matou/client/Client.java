package matou.client;

import matou.file.FileUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author kev
 *         la classe Client est utilise en synergie avec la classe Server,
 *         elle represente l'interface sur terminale qu'utilise un client
 *         pour communiquer avec les autres clients.
 *         il se lance grace a son main qui construit le client et
 *         lance sa fonction principale launch.
 */
public class Client {

    private final static Charset UTF8_charset = Charset.forName("UTF8");

    /*
     * Le temps que doit attendre le programme entre deux actualisation de la liste
     */
    private final static long ACTU_LIST_TIME_MILLIS = 1000 * 5;
    private final static int BUFFER_SIZE = 1024;
    private final Scanner scan;
    private final List<Thread> tabThreadClient;
    private final ConcurrentHashMap<String, SocketChannel> friends;
    private final SocketChannel socket;
    private final ServerSocketChannel serverSocketChannel;
    private String nickname;
    /*
     * map des client et des amis, mapClient ne peux pas etre final car il est
     * recrer a chaque fois que l'on actualise la liste
     */
    private ConcurrentHashMap<String, InetSocketAddress> mapClient;
    private BlockingQueue<String> queueAll = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueACK = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueConnect = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueFile = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueUser = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueDest = new ArrayBlockingQueue<>(1);
    private BlockingQueue<String> queueClient = new ArrayBlockingQueue<>(1);
    private BlockingQueue<ByteBuffer> queueServer = new ArrayBlockingQueue<>(1);
    private boolean canAccept = false;
    private boolean end = false;

    private Client(String host, int port) throws IOException {

        mapClient = new ConcurrentHashMap<>();
        friends = new ConcurrentHashMap<>();
        socket = SocketChannel.open();
        socket.connect(new InetSocketAddress(host, port));

        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(serverSocketChannel.getLocalAddress());
        scan = new Scanner(System.in);

        tabThreadClient = new ArrayList<>();
    }

    private static void usage() {
        System.out.println("java matou.client.Client localhost 7777");
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 7777;

        if (args.length != 0) {
            if (args.length != 2) {
                usage();
                return;
            }
            host = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            System.out.println("No arguments giving\nStarting client with default values\nhost = localhost\nport = 7777");
        }
        try {
            Client client = new Client(host, port);
            client.launch();
        } catch (IOException e) {
            System.out.println("Serveur deconnecter");
            e.printStackTrace();
        }

    }

    /**
     * cette fonction lance l'interface client qui permet
     * la bonne synchronisation des messages entre le client
     * et le serveur et ses autres clients.
     */
    public void launch() {
        pseudoRegister();
        if (end) {
            return;
        }
        sendInfoServer();
        if (end) {
            return;
        }
        Thread tReadClient = threadReadClient();
        tabThreadClient.add(tReadClient);
        tReadClient.start();
        receiveServer();
    }

    private void sendInfoServer() {
        String serverBeforeStrip;
        try {
            serverBeforeStrip = serverSocketChannel.getLocalAddress().toString();
        } catch (IOException e) {
            System.err.println("Erreur lors de l'ecriture d'un paquet du serveur , Serveur deconnecter");
            exitClient();
            end = true;
            return;
        }
        ByteBuffer bInfoServer = UTF8_charset.encode(serverBeforeStrip.replace("/", ""));
        ByteBuffer bInfoServerToServer = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + serverBeforeStrip.length() - 1);

        bInfoServerToServer.put(PacketType.E_ADDR_SERV_CLIENT.getValue())
                .putInt(serverBeforeStrip.length() - 1).put(bInfoServer);
        bInfoServerToServer.flip();

        try {
            socket.write(bInfoServerToServer);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'ecriture d'un paquet du serveur , Serveur deconnecter");
            exitClient();
            end = true;
        }
    }

    private void pseudoRegister() {
        while (!sendPseudo()) {
            System.out.println("Le pseudo est deja pris.");
        }
    }

    private boolean sendPseudo() {
        System.out.println("Quel pseudo souhaitez vous avoir ?");
        if (scan.hasNextLine()) {
            String tmp;
            do {
                tmp = scan.nextLine();
                if (tmp.length() > BUFFER_SIZE) {
                    System.out.println("Pseudo trop long , veuillez choisir un pseudo plus court");
                }
            } while (tmp.length() > BUFFER_SIZE);
            ByteBuffer bNickName = UTF8_charset.encode(tmp);
            ByteBuffer bNickNameToServer = ByteBuffer.allocate(Byte.BYTES
                    + Integer.BYTES + tmp.length());
            bNickNameToServer.put(PacketType.E_PSEUDO.getValue())
                    .putInt(tmp.length())
                    .put(bNickName);
            bNickNameToServer.flip();
            try {
                socket.write(bNickNameToServer);
            } catch (IOException e1) {
                System.err
                        .println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecter");
                exitClient();
                end = true;
                return true;
            }
            // lis des donnee, cherche le byte R_PSEUDO et jete le reste, s'il
            // ne la pas trouver
            // dans tout le buffer il recommence a lire.
            ByteBuffer bReceive = ByteBuffer.allocate(BUFFER_SIZE);
            do {
                try {
                    while (socket.read(bReceive) == 0) ;
                } catch (IOException e) {
                    System.err
                            .println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecter");
                    exitClient();
                    end = true;
                    return true;
                }
                bReceive.flip();
                while (bReceive.get() != PacketType.R_PSEUDO.getValue() && bReceive.hasRemaining()) ;
            } while (!bReceive.hasRemaining());
            int test = bReceive.getInt();
            if (test == 1) {
                System.out.println("Pseudo enregistrer");
                nickname = tmp;
                return true;
            } else if (test == 2) {
                System.out.println("Vous avez deja choisi votre pseudo");
                return false;
            } else if (test == 0) {
                return false;
            }
            throw new IllegalStateException("read int test unvailable");
        }
        throw new IllegalStateException("line never read");
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

    private void receiveServer() {
        long deb = System.currentTimeMillis();
        ByteBuffer buffByte;
        Thread threadSend = threadSend();
        tabThreadClient.add(threadSend);
        threadSend.start();
        Thread threadRead = threadRead();
        tabThreadClient.add(threadRead);

        threadRead.start();
        while (!Thread.interrupted()) {
            if (end) {
                exitClient();
                return;
            }

            if (System.currentTimeMillis() - deb > ACTU_LIST_TIME_MILLIS) {
                demandeList();
                deb = System.currentTimeMillis();
            }
            try {
                if ((buffByte = queueServer.poll(100, TimeUnit.MILLISECONDS)) == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                System.err.println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecter");
                exitClient();
                end = true;
                return;
            }

            buffByte.flip();
            while (buffByte.hasRemaining()) {
                Byte b = buffByte.get();
                PacketType bb2 = PacketType.encode(b);
                switch (bb2) {
                    case E_CO_CLIENT_TO_CLIENT:
                        decodeCoClient(buffByte);
                        break;
                    case R_LIST_CLIENT_CO:
                        decodeRList(buffByte);
                        break;
                    case E_M_ALL:
                        decodeMessageAll(buffByte);
                        break;
                    case R_CO_CLIENT_TO_CLIENT:
                        decodeR_CO_CLIENT_TO_CLIENT(buffByte);
                        break;
                    default:
                        System.err.println("Error : Code inconnu " + b);
                        break;
                }
            }
            buffByte.compact();
        }
    }

    private Thread threadRead() {
        return new Thread(() -> {
            ByteBuffer buff = ByteBuffer.allocate(BUFFER_SIZE);
            try {
                while (socket.read(buff) != -1) {
                    if (!buff.hasRemaining()) {
                        ByteBuffer tmp = buff.duplicate();
                        buff = ByteBuffer.allocate(tmp.capacity() * 2);
                        buff.put(tmp);
                        continue;
                    }
                    queueServer.put(buff);
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Erreur lors de lecture d'un paquet du serveur , Serveur deconnecter");
                exitClient();
                end = true;
            }
        });
    }

    private Thread threadSend() {
        return new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    send();
                } catch (InterruptedException e) {
                    exitClient();
                    end = true;
                }
            }
        });
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
        System.out.println("L'utilisateur " + user + " souhaiterai se connecter avec vous,\npour ce faire, vous devez tapez /accept " + user);
        canAccept = true;
    }

    private void decodeRList(ByteBuffer buffByte) {
        int size;
        mapClient = new ConcurrentHashMap<>();
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

    private void decodeR_CO_CLIENT_TO_CLIENT(ByteBuffer buffByte) {

        int response = buffByte.getInt();
        if (response == 0) {
            System.out.println("Le client demande n'existe pas");
        } else if (response == 1) {
            System.out.println("La requete a bien ete transmise au client");
        }
    }

    private void send() throws InterruptedException {
        String arg0, arg1;
        if ((arg0 = queueAll.poll(50, TimeUnit.MILLISECONDS)) != null) {
            sendMessageAll(arg0);
        }
        if ((arg0 = queueACK.poll(50, TimeUnit.MILLISECONDS)) != null) {
            sendPseudoAck(arg0);
        }
        if ((arg0 = queueConnect.poll(50, TimeUnit.MILLISECONDS)) != null) {
            sendPseudoConnect(arg0);
        }
        if ((arg0 = queueFile.poll(50, TimeUnit.MILLISECONDS)) != null && (arg1 = queueUser.poll(50, TimeUnit.MILLISECONDS)) != null) {
            sendFile(arg0, arg1);
        }
        if ((arg0 = queueClient.poll(50, TimeUnit.MILLISECONDS)) != null && (arg1 = queueDest.poll(50, TimeUnit.MILLISECONDS)) != null) {
            sendMessageToClient(arg0, arg1);
        }
    }

    private void sendMessageAll(String messageAll) {
        ByteBuffer buffMessage = UTF8_charset.encode(messageAll);
        ByteBuffer buffNickName = UTF8_charset.encode(nickname);

        ByteBuffer buffSendAll = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES
                + nickname.length() + Integer.BYTES + messageAll.length());
        buffSendAll.put(PacketType.E_M_ALL.getValue())
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
    }

    private void sendPseudoAck(String pseudoACK) {
        ByteBuffer buffSendACK = ByteBuffer.allocate(BUFFER_SIZE);
        buffSendACK.put(PacketType.ACK_CO_CLIENT.getValue())
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
        if (socketACK != null) {
            friends.put(pseudoACK, socketACK);
            System.out.println("Connexion accepter");
            System.out.println("Vous etes maintenant connecter avec " + pseudoACK);
            Thread tmp = clientClient(socketACK);
            tabThreadClient.add(tmp);
            tmp.start();
        }

    }

    private void sendPseudoConnect(String pseudoConnect) {
        // envoyer demande a pseudo connect
        System.out.println("Demande de connexion envoyer");
        ByteBuffer buffConnect = ByteBuffer.allocate(BUFFER_SIZE);
        buffConnect.put(PacketType.E_CO_CLIENT_TO_CLIENT.getValue())
                .putInt(pseudoConnect.length())
                .put(UTF8_charset.encode(pseudoConnect))
                .putInt(nickname.length()).put(UTF8_charset.encode(nickname));
        buffConnect.flip();

        Thread tmp = serverClient();
        tmp.start();
        try {
            socket.write(buffConnect);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie de la demande de connection, serveur deconnecter");
            exitClient();
        }
    }

    private void sendFile(String fileName, String userName) {
        Thread t = new Thread(() -> {
            ByteBuffer buff;
            try {
                buff = FileUtils.readAndStoreInBuffer(fileName);
                if (buff != null) {
                    ByteBuffer buffSendFile = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + nickname.length() + Integer.BYTES + fileName.length() + Integer.BYTES + buff.remaining());
                    ByteBuffer buffFileName = UTF8_charset.encode(fileName);
                    buffSendFile.put(PacketType.F_CLIENT_TO_CLIENT.getValue())
                            .putInt(nickname.length())
                            .put(UTF8_charset.encode(nickname))
                            .putInt(fileName.length())
                            .put(buffFileName)
                            .putInt(buff.remaining())
                            .put(buff);
                    buffSendFile.flip();
                    SendToFriend(userName, buffSendFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        tabThreadClient.add(t);
        t.start();
    }

    private void sendMessageToClient(String messageToClient, String dest) {
        ByteBuffer buffSend = ByteBuffer.allocate(BUFFER_SIZE);
        buffSend.put(PacketType.M_CLIENT_TO_CLIENT.getValue())
                .putInt(nickname.length())
                .put(UTF8_charset.encode(nickname))
                .putInt(messageToClient.length())
                .put(UTF8_charset.encode(messageToClient));
        buffSend.flip();
        SendToFriend(dest, buffSend);
        System.out.println("Message envoyer");
    }

    private void SendToFriend(final String destCpy, ByteBuffer buffSend) {
        friends.forEach((name, socketFriend) -> {
            if (name.equals(destCpy)) {
                try {
                    socketFriend.write(buffSend);
                } catch (IOException e) {
                    System.err.println("Erreur lors de l'envoie du message au client " + destCpy);
                }
            }
        });
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
                    s.close();
                    Thread.currentThread().interrupt();
                    return;
                }
                buff.flip();
                byte b = buff.get();
                if (b != PacketType.ACK_CO_CLIENT.getValue()) {
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
                        System.out.println("Client " + name + " s'est deconnecter");
                    }
                });
                Thread.currentThread().interrupt();
                return;
            }
            buffRead.flip();
            byte b = buffRead.get();
            PacketType bb2 = PacketType.encode(b);
            int size;
            switch (bb2) {
                case M_CLIENT_TO_CLIENT:
                    int sizeName = buffRead.getInt();
                    ByteBuffer bName = ByteBuffer.allocate(sizeName);
                    for (int i = 0; i < sizeName; i++) {
                        bName.put(buffRead.get());
                    }
                    bName.flip();

                    size = buffRead.getInt();
                    ByteBuffer bMessage = ByteBuffer.allocate(size);
                    for (int i = 0; i < size; i++) {
                        bMessage.put(buffRead.get());
                    }
                    bMessage.flip();

                    System.out.println("[private] from " + UTF8_charset.decode(bName) + " : " + UTF8_charset.decode(bMessage));

                    break;
                case F_CLIENT_TO_CLIENT:
                    Thread t = receiveFile(buffRead);
                    tabThreadClient.add(t);
                    t.start();
                    break;
                default:
                    System.err.println("Error : Unkown code " + b);
                    break;
            }
        }
    }

    private Thread receiveFile(ByteBuffer buff) {
        return new Thread(() -> {
            int sizeNameClient = buff.getInt();
            ByteBuffer buffNameClient = copyPartialBuffer(buff, sizeNameClient);
            int sizeFileName = buff.getInt();
            ByteBuffer buffFileName = copyPartialBuffer(buff, sizeFileName);
            String fileName = UTF8_charset.decode(buffFileName).toString();
            System.out.println("L'utilisateur " + UTF8_charset.decode(buffNameClient) + " vous envoie le fichier " + fileName);
            int sizeFile = buff.getInt();
            ByteBuffer buffFile = copyPartialBuffer(buff, sizeFile);

            FileUtils.readInBufferAndWriteInFile(buffFile, fileName);
        });
    }

    private void demandeList() {
        ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES);
        buff.put(PacketType.D_LIST_CLIENT_CO.getValue());
        buff.flip();
        try {
            socket.write(buff);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoie de la demande liste, serveur deconnecter");
            exitClient();
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

    private Thread threadReadClient() {

        return new Thread(
                () -> {
                    listeCommande();
                    System.out.println("Que souhaitez vous faire?");
                    while (scan.hasNextLine()) {
                        if (end) {
                            break;
                        }
                        String line = scan.nextLine();
                        String[] words = line.split(" ");

                        // /////////////////////////////////////////////////////////
                        if (words[0].equals("/all")) {
                            if (words.length < 2) {
                                System.err.println("Vous n'avez pas mis de message");
                            }
                            if (words[1].length() < BUFFER_SIZE) {
                                try {
                                    queueAll.put(constructMessage(1, words));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } else {
                                System.out.println("Message trop long, veuillez ecrire un message plus court");
                            }
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/commandes")) {
                            listeCommande();
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/w")) {
                            if (words.length < 3) {
                                System.err.println("Vous n'avez pas mis de message");
                            }
                            try {
                                queueDest.put(words[1]);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            if (friends.containsKey(words[1])) {
                                if (words[2].length() < BUFFER_SIZE) {
                                    try {
                                        queueClient.put(constructMessage(2, words));
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    System.out.println("Message trop long, veuillez ecrire un message plus court");
                                }
                            } else {
                                if (mapClient.containsKey(words[1])) {
                                    System.out.println("Il faut vous connecter au client " + words[1] + " avant de lui envoyer des messages privee");
                                } else {
                                    System.out.println("Le client " + words[1] + " n'existe pas");
                                }
                            }
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/accept")) {
                            if (canAccept) {
                                if (words.length < 2) {
                                    System.err.println("Il faut donner un nom d'utilisateur sur lequel accepter");
                                } else if (words.length > 2) {
                                    System.err.println("Trop d'argument , format /accept user");
                                } else {
                                    try {
                                        queueACK.put(words[1]);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    canAccept = false;
                                }
                            } else {
                                System.err.println("Vous ne pouvez pas accepter de connexion si personne ne vous le demande");
                            }
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/connect")) {
                            if (!mapClient.isEmpty()) {
                                if (words.length < 2) {
                                    System.err.println("Il faut donner un nom d'utilisateur sur lequel se connecter");
                                } else if (words.length > 2) {
                                    System.err.println("Trop d'argument , format /connect user");
                                } else {
                                    if (!words[1].equals(nickname)) {
                                        if (!friends.containsKey(words[1])) {
                                            if (mapClient.containsKey(words[1])) {
                                                try {
                                                    queueConnect.put(words[1]);
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }
                                            } else {
                                                System.out.println("Le client " + words[1] + " n'existe pas");
                                            }
                                        } else {
                                            System.out.println("Vous etes deja connecte au client " + words[1]);
                                        }
                                    }
                                }
                            } else {
                                System.out.println("Il n'y a personne de connecter sur le serveur autre que vous");
                            }
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/file")) {
                            if (words.length < 2) {
                                System.err.println("empty file name");
                            } else if (words.length > 3) {
                                System.err.println("too much argument");
                            } else {
                                try {
                                    queueUser.put(words[1]);
                                    queueFile.put(words[2]);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/friends")) {
                            printFriends();
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/clients")) {
                            printClients();
                        }
                        // /////////////////////////////////////////////////////////
                        else if (words[0].equals("/exit")) {
                            exitClient();
                            return;
                        }
                        // /////////////////////////////////////////////////////////
                        else {
                            System.err.println("Commande inconnu : " + words[0]);
                            listeCommande();
                        }
                    }
                });
    }

    private void exitClient() {
        ByteBuffer b = ByteBuffer.allocate(Byte.BYTES);
        b.put(PacketType.DC_PSEUDO.getValue());
        b.flip();
        try {
            socket.write(b);
        } catch (IOException e) {
            System.err.println("Erreur lors de la deconnection, serveur deconnecter");
        }
        end = true;
        scan.close();
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
        tabThreadClient.forEach(Thread::interrupt);
    }

    private void listeCommande() {
        System.out.println("Voici les commandes utilisateur :\n"
                + "/commandes : Lister les commandes\n"
                + "/w pseudo message : Envoyer un message a pseudo\n"
                + "/all monMessage : Envoyer monMessage a tous les clients\n"
                + "/connect pseudo : Demande de connection au client nomme pseudo\n"
                + "/accept pseudo : Accepter la connection au client nomme pseudo\n"
                + "/file pseudo nomDuFichier : Envoyer un fichier a pseudo , taille maximal 50Mo\n"
                + "/friends : Affiche la liste des personnes avec qui on est connecter\n"
                + "/clients : Affiche la liste des clients connecter au serveur\n"
                + "/exit : Quitter le programme");
    }

    private ByteBuffer copyPartialBuffer(ByteBuffer in, int size) {
        ByteBuffer tempo = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            tempo.put(in.get());
        }
        tempo.flip();
        return tempo;
    }

    @Override
    public String toString() {
        return "Client{" +
                "  \nscan=" + scan +
                "  \ntabThreadClient=" + tabThreadClient +
                "  \nfriends=" + friends +
                "  \nsocket=" + socket +
                "  \nserverSocketChannel=" + serverSocketChannel +
                "  \nnickname='" + nickname + '\'' +
                "  \nmapClient=" + mapClient +
                "  \nqueueAll=" + queueAll +
                "  \nqueueACK=" + queueACK +
                "  \nqueueConnect=" + queueConnect +
                "  \nqueueFile=" + queueFile +
                "  \nqueueUser=" + queueUser +
                "  \nqueueDest=" + queueDest +
                "  \nqueueClient=" + queueClient +
                "  \nqueueServer=" + queueServer +
                "  \ncanAccept=" + canAccept +
                "  \nend=" + end +
                "\n}";
    }

    private enum PacketType {
        E_PSEUDO(1),
        E_CO_CLIENT_TO_CLIENT(2),
        ACK_CO_CLIENT(3),
        /* envoie d'un message ou d'un fichier a un client */
        M_CLIENT_TO_CLIENT(4),
        F_CLIENT_TO_CLIENT(5),
        /* deconnection du client */
        DC_PSEUDO(6),
        /* envoie et reception de la liste des clients */
        D_LIST_CLIENT_CO(7),
        R_LIST_CLIENT_CO(8),
        /* Concerne l'envoie et la reception */
        E_M_ALL(9),
        /* reception pseudo */
        R_PSEUDO(10),
        /* envoie adresse du serveur du client au serveur principal */
        E_ADDR_SERV_CLIENT(11),
        R_CO_CLIENT_TO_CLIENT(12);
        private final byte value;

        PacketType(int value) {
            this.value = (byte) value;
        }

        public static PacketType encode(byte b) {
            for (PacketType p : PacketType.values()) {
                if (p.getValue() == b) {
                    return p;
                }
            }
            throw new IllegalArgumentException("Wrong byte b : " + b);
        }

        public byte getValue() {
            return value;
        }
    }
}
