package matou.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
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
    
    /*Le temps que doit attendre le programme entre deux actualisation de la liste*/
	private static final long ACTU_LIST_TIME_MILLIS = 1000*5;
	
    boolean end = false;
    private String nickname;
    
    private HashMap<String, InetSocketAddress> mapClient;
    private HashMap<String, SocketChannel> friend;
    
    private SocketChannel socket;
	private ServerSocketChannel serverSocketChannel;
	
    private int BUFFER_SIZE = 1024;
    private String messageAll = null;
    private String pseudoACK = null;
    private String pseudoConnect = null;
    private String fileName = null;
    private String userName = null;

    public Client(String host, int port) throws IOException {

        mapClient = new HashMap<>();
        friend = new HashMap<>();
        socket = SocketChannel.open();
        socket.connect(new InetSocketAddress(host, port));
        socket.configureBlocking(false);
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(serverSocketChannel.getLocalAddress());

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
        client.launch();
    }

    private void pseudoRegister() throws IOException {
        while (!sendPseudo()) {
            System.out.println("le pseudo est deja pris.");
        }
    }

    private boolean sendPseudo() throws IOException {
        System.out.println("Quel pseudo souhaitez vous avoir ?");

        Scanner scan = new Scanner(System.in);

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
        //lis des donnée, cherche le byte R_PSEUDO et jete le reste, s'il ne la pas trouver
        //dans tout le buffer il recommence a read.
        do{
	        while (socket.read(bReceive) == 0) ;
	        bReceive.flip();
	        bReceive.get();
	        while(bReceive.get() != R_PSEUDO && bReceive.hasRemaining());
        }while(!bReceive.hasRemaining());
        
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

    public void launch() throws IOException {
        int size;
        threadRead().start();
        long deb = System.currentTimeMillis();
        while (!Thread.interrupted()) {
            if (end) {
                break;
            }
            send();
            if(System.currentTimeMillis()-deb>ACTU_LIST_TIME_MILLIS){
            	demandeList();
            	deb=System.currentTimeMillis();
            }
//            demandeList(); -> ajouter un timer qui demandera tout les X temps , plutot que a chaque tour de boucle
            ByteBuffer buffByte = ByteBuffer.allocate(BUFFER_SIZE);
            ByteBuffer buffName;
            if (null == (buffByte = readAll(buffByte, socket))) {
                continue;
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
                        break;
                    case ACK_CO_CLIENT:
                        size = buffByte.getInt();
                        buffName = ByteBuffer.allocate(size);
                        for (int i = 0; i < size; i++) {
                            buffName.put(buffByte.get());
                        }
                        buffName.flip();
                        break;
                    case M_CLIENT_TO_CLIENT:
                        break;
                    case F_CLIENT_TO_CLIENT:
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
                            addList(buffSocket, buffClient);
                            size--;
                        }
                        mapClient.forEach((key,value)->System.out.println(key));
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
                        System.out.println(UTF8_charset.decode(buffPseudo) + ":" + UTF8_charset.decode(buffMessenger));
                        break;
                    default:
                        System.err.println("Error : Unkown code " + b);
                        break;
                }
            }
        }
    }

    private void send() throws IOException {
        if (messageAll != null) {
            ByteBuffer buffMessage = UTF8_charset.encode(messageAll);
            ByteBuffer buffNickName = UTF8_charset.encode(nickname);

            ByteBuffer buffSendAll = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + nickname.length() + Integer.BYTES + messageAll.length());
            buffSendAll.put(M_ALL)
                    .putInt(nickname.length())
                    .put(buffNickName)
                    .putInt(messageAll.length())
                    .put(buffMessage);
            buffSendAll.flip();
            socket.write(buffSendAll);
            messageAll = null;
        }
        if (pseudoACK != null) {
            ByteBuffer buffSendACK = ByteBuffer.allocate(BUFFER_SIZE);
            buffSendACK.put(ACK_CO_CLIENT);
            buffSendACK.putInt(nickname.length());
            buffSendACK.put(UTF8_charset.encode(nickname));
            buffSendACK.flip();

            SocketChannel socketACK = SocketChannel.open();
            socketACK.connect(mapClient.get(pseudoACK));
            friend.put(pseudoACK, socketACK);
            socketACK.write(buffSendACK);
            pseudoACK = null;
        }
        if (pseudoConnect != null) {
            //envoyer demande a pseudo connect
        	ByteBuffer buffConnect = ByteBuffer.allocate(BUFFER_SIZE);
        	buffConnect.put(CO_CLIENT_TO_CLIENT);
        	buffConnect.putInt(pseudoConnect.length());
        	buffConnect.put(UTF8_charset.encode(pseudoConnect));
        	buffConnect.putInt(nickname.length());
        	buffConnect.put(UTF8_charset.encode(nickname));
        	buffConnect.flip();
        	ServeurClient().start();
        	socket.write(buffConnect);
        	
            //se mettre en mode serveur
            pseudoConnect = null;
        }
        if (fileName != null && userName != null) {
        	//verifier que le client est dans la liste d'ami
            //lire dans filename
            //envoyer ce qui est lu en thread
            userName = null;
            fileName = null;
        }

    }

    private Thread ServeurClient() throws IOException {
    	return new Thread( ()->{
			SocketChannel s;
			try {
				s = serverSocketChannel.accept();
				ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES+Byte.BYTES);
				ByteBuffer buffName = ByteBuffer.allocate(BUFFER_SIZE);
				readAll(buff, s);
				buff.flip();
				byte b = buff.get();
				if(b!=ACK_CO_CLIENT){
					System.out.println("erreur");
				}
				int size = buff.getInt();
				for(int i=0;i<size;i++){
					buffName.put(buff.get());
				}
				buffName.flip();
	    		friend.put(UTF8_charset.decode(buffName).toString(), s);
	    		//read
			} catch (Exception e) {
				e.printStackTrace();
			}
    	});
	}
    private Thread clientClient(){
    	return null;
    	//return new Thread( ()->{
		//attendre le read
    	//});
    }

	private void demandeList() throws IOException {
        ByteBuffer buff = ByteBuffer.allocate(Byte.BYTES);
        buff.put(D_LIST_CLIENT_CO);
        buff.flip();
        socket.write(buff);
    }
   /* private Thread threadClient(String pseudo) {
        return new Thread(() -> {
            //scanner.read
            //si il demande la connection il modifie requeteCo, sil demande un messageAll il modifie messageAll...
        });
    }*/

    private void addList(ByteBuffer buffSocket, ByteBuffer buffClient) {
        String socketChan = UTF8_charset.decode(buffSocket).toString();
        String[] token = socketChan.split(":");
        if (token.length != 2) {
            System.out.println("too much or too few data for socket");
        }
        InetSocketAddress in = new InetSocketAddress(token[0], Integer.parseInt(token[1]));
        mapClient.put(UTF8_charset.decode(buffClient).toString(), in);
    }

    private void actualiseListFriend() {
        friend.forEach((key, value) -> {
            if (!mapClient.containsKey(key)) {
                friend.remove(key);
            }
        });
    }

    private Thread threadRead() {

        return new Thread(() -> {
            listeCommande();
            System.out.println("Que souhaitez vous faire?");
            try (Scanner sc = new Scanner(System.in)) {
                while (sc.hasNextLine()) {

                    String line = sc.nextLine();
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
                        System.out.println("messageAll = " + messageAll);
                    }
                    ///////////////////////////////////////////////////////////
                    else if (words[0].equals("/commandes")) {
                        listeCommande();
                    }
                    ///////////////////////////////////////////////////////////
                    else if (words[0].equals("/accept")) {
                        if (words.length < 2) {
                            System.err.println("empty user");
                        } else if (words.length > 2) {
                            System.err.println("too much argument");
                        } else {
                            pseudoACK = words[2];
                        }
                    }
                    ///////////////////////////////////////////////////////////
                    else if (words[0].equals("/connect")) {
                        if (words.length < 2) {
                            System.err.println("empty user");
                        } else if (words.length > 2) {
                            System.err.println("too much argument");
                        } else {
                            pseudoConnect = words[2];
                        }
                    }
                    ///////////////////////////////////////////////////////////
                    else if (words[0].equals("/file")) {
                        if (words.length < 2) {
                            System.err.println("empty file name");
                        } else if (words.length > 2) {
                            System.err.println("too much argument");
                        } else {
                            userName = words[2];
                            fileName = words[3];
                        }
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
                        sc.close();
                        break;
                    }
                    ///////////////////////////////////////////////////////////
                    else {
                        System.err.println("Commande inconnu : " + words[0]);
                        listeCommande();
                    }
                }
            }


        });
    }

    private void listeCommande() {
        System.out.println("voici les commandes utilisateur :\n"
                + "/commandes pour lister les commande\n"
                + "/all monMessage pour envoyer un message a tout les clients\n"
                + "/connect pseudo pour demander a vous connecter au client nomme pseudo\n"
                + "/accept pseudo pour accepter la connection au client nomme pseudo\n"
                + "/file nomDuFichier pseudo pour envoyer un fichier a pseudo\n"
                + "/exit pour quittez la messagerie");
    }
}
