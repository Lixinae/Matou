import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Scanner;

public class Client {

	private String nickname;
	private HashMap<String, SocketChannel> map;
	private SocketChannel socket;
	private int BUFFER_SIZE = 1024;
	private String messageAll = null;
	private String requeteCo = null;
	private final static Charset UTF8_charset = Charset.forName("UTF8");
	/*byte pour que le serveur sache qu'on envoie son pseudo*/
	private final static byte E_PSEUDO = 1;
	/* connection et accusé de reception de la connection au client */
	private final static byte CO_CLIENT_TO_CLIENT = 2;
	private final static byte ACK_CO_CLIENT = 3;

	/* envoie d'un message ou d'un fichier à un client */
	private final static byte M_CLIENT_TO_CLIENT = 4;
	private final static byte F_CLIENT_TO_CLIENT = 5;
	/* Concerne l'envoie et la reception */
	private final static byte M_ALL = 9;

	public Client(String host, int port, String nickname) throws IOException {
		this.nickname = nickname;
		map = new HashMap<>();
		socket = SocketChannel.open();
		socket.connect(new InetSocketAddress(host, port));
		sendPseudo(nickname);
	}

	private void sendPseudo(String nickname) throws IOException {
		ByteBuffer bNickName = UTF8_charset.encode(nickname);
		ByteBuffer bNickNameToServer = ByteBuffer.allocate(BUFFER_SIZE);
		bNickNameToServer.put(E_PSEUDO);
		bNickNameToServer.put(bNickName);
		socket.write(bNickNameToServer);
	}

	// Lit ce que le socketChannel reÃ§oit et le stock dans le buffer,
	// Si le buffer est trop petit , la taille est automatiquement augmentÃ©
	// jusqu'a ce qu'il ne soit plus plein
	private ByteBuffer readAll(ByteBuffer bbIn, SocketChannel sc)
			throws IOException {
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
		int size=0;
		threadRead().start();
		while (!Thread.interrupted()) {
			actualiseListe();
			ByteBuffer buffByte = ByteBuffer.allocate(BUFFER_SIZE);
			if (null == (buffByte = readAll(buffByte, socket))) {
				continue;
			}
			buffByte.flip();
			while(buffByte.hasRemaining()){
				Byte b = buffByte.get();
				switch (b) {
				case CO_CLIENT_TO_CLIENT:
					actualiseListe();
					size = buffByte.getInt();
					ByteBuffer buffName = ByteBuffer.allocate(size);
					System.out.println(UTF8_charset.decode(buffName) + "souhaiterai se connecter avec vous.");
					break;
				case ACK_CO_CLIENT:
					size = buffByte.getInt();
					//nous faut t il l'host et le port?
					threadClient(null).start();//socket en arg
					break;
				case M_CLIENT_TO_CLIENT:
					break;
				case F_CLIENT_TO_CLIENT:
					break;
				case M_ALL:
					size = buffByte.getInt();
					ByteBuffer buffPseudo = ByteBuffer.allocate(size);
					buffPseudo.put(buffByte);
					size = buffByte.getInt();
					ByteBuffer buffMessenger = ByteBuffer.allocate(size);
					System.out.println(UTF8_charset.decode(buffPseudo)+":"+UTF8_charset.decode(buffMessenger));
					break;
				default:
				}
			}

			if (messageAll != null) {
				ByteBuffer buffMessage = UTF8_charset.encode(messageAll);
				ByteBuffer buffSendAll = ByteBuffer.allocate(BUFFER_SIZE);
				buffSendAll.put(M_ALL);
				buffSendAll.putInt(nickname.length());
				buffSendAll.put(UTF8_charset.encode(nickname));
				buffSendAll.putInt(buffMessage.capacity());
				buffSendAll.put(buffMessage);
				messageAll=null;
				socket.write(buffSendAll);
				
			}
			if (requeteCo != null) {

			}
		}
	}

	private void actualiseListe() {
		//mettre la liste des client dans la map
	}

	private Thread threadClient(SocketChannel sc) {
		return new Thread(() -> {
			//scanner.read
			//si il demande la connection il modifie requeteCo, sil demande un messageAll il modifie messageAll...
		});
	}

	private Thread threadRead() {
		return new Thread(() -> {
			listeCommande();
			System.out.println("Que souhaitez vous faire?");
			Scanner sc = new Scanner(System.in);
			while(sc.hasNextLine()){
				boolean end=false;
				String line = sc.nextLine();
				String[] words = line.split(" ");
				switch(words[0]){
				case "/all":
					messageAll = words[1];
					break;
				case "/commande":
					listeCommande();
					break;
				case "/connect":
					break;
				case "/file":
					break;
				case "/exit":
					sc.close();
					end = true;
					break;
				default:
					System.out.println("commande inconnu");
					listeCommande();
				}
				if(end){
					break;
				}
			}
		});
	}

	private void listeCommande() {
		System.out.println("voici les commandes utilisateur :\n"
				+ "/commande pour lister les commande\n"
				+ "/all monMessage pour envoyer un message à tout les clients\n"
				+ "/connect pseudo pour vous connecter au client nommé pseudo\n"
				+ "/file nomDuFichier pseudo pour envoyer un fichier à pseudo\n"
				+ "/exit pour quittez la messagerie");
	}

	public void decodePack(SocketChannel socketChannel, ByteBuffer buffer) {
		buffer.flip();
		if (buffer.remaining() < Byte.BYTES) {
			throw new IllegalStateException("wrong packet received");
		}
		Byte b = buffer.get();
		switch (b) {
		case CO_CLIENT_TO_CLIENT:
			String nom = UTF8_charset.decode(buffer).toString();
			System.out.println(nom
					+ " souhaiterai entrer en conversation privée avec vous.");
			map.put(nom, socketChannel);
		case ACK_CO_CLIENT:

		case M_CLIENT_TO_CLIENT:

		case F_CLIENT_TO_CLIENT:

		case M_ALL:

		default:
			throw new IllegalStateException("wrong byte read");
		}
	}

	private static void usage() {
		System.out.println("java Client localhost 7777 MyNickname");
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			usage();
			return;
		}
		Client client = new Client(args[0], Integer.parseInt(args[1]), args[2]);
		client.launch();

	}

}
