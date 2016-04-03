import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Set;

public class Client {

	private String nickname;
	private HashMap<String, SocketChannel> map;
	private SocketChannel socket;
	private int BUFFER_SIZE = 1024;
	private String messageAll = null;
	private String requeteCo = null;
	private final static Charset UTF8_charset = Charset.forName("UTF8");
	/* connection et accusÈ de reception de la connection au client */
	private final static byte CO_CLIENT_TO_CLIENT = 2;
	private final static byte ACK_CO_CLIENT = 3;

	/* envoie d'un message ou d'un fichier ‡ un client */
	private final static byte M_CLIENT_TO_CLIENT = 4;
	private final static byte F_CLIENT_TO_CLIENT = 5;
	/* Concerne l'envoie et la reception */
	private final static byte M_ALL = 9;

	public Client(String host, int port, String nickname) throws IOException {
		this.nickname = nickname;
		map = new HashMap<>();
		socket = SocketChannel.open();
		socket.connect(new InetSocketAddress(host, port));
		ByteBuffer b = UTF8_charset.encode(nickname);
		socket.write(b);
	}

	// Lit ce que le socketChannel re√ßoit et le stock dans le buffer,
	// Si le buffer est trop petit , la taille est automatiquement augment√©
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
		threadClient().start();
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
					int size = buffByte.getInt();
					ByteBuffer buffName = ByteBuffer.allocate(size);
					System.out.println(UTF8_charset.decode(buffName) + "souhaiterai se connecter avec vous.");
					break;
				case ACK_CO_CLIENT:
					threadClient(socketClient).start();
					break;
				case M_CLIENT_TO_CLIENT:
					break;
				case F_CLIENT_TO_CLIENT:
					break;
				case M_ALL:
					break;
				default:
				}
			}

			if (messageAll != null) {
				// envoie messageAll a tous
				// messageAll=null
			}
			if (requeteCo != null) {

			}
		}
	}

	private void actualiseListe() {
		//mettre la liste des client dans la map
	}

	private Thread threadClient() {
		return new Thread(() -> {
			//scanner.read
			//si il demande la connection il modifie requeteCo, sil demande un messageAll il modifie messageAll...
		});
	}

	private Thread threadRead() {
		return new Thread(() -> {

		});
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
					+ " souhaiterai entrer en conversation privÈe avec vous.");
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
