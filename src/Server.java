import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class Server {

    private final static int BUFF_SIZE = 8;

    /* Codes pour la reception de paquets */
    private final static byte E_PSEUDO = 1;
    private final static byte DC_PSEUDO = 6;
    private final static byte D_LIST_CLIENT_CO = 7;
    private final static byte E_M_ALL = 9;

    /* Code pour l'envoie de paquets */
    private final static byte M_CLIENT_TO_CLIENT = 4;
    private final static byte R_LIST_CLIENT_CO = 8;

    private final static byte R_PSEUDO = 10;


//    private final static int TIME_OUT = 1000;

    //    private final Map<SocketChannel, Long> clientTimer = new HashMap<>();
//    private final static byte R_LIST_CLIENT_CO = 8;

    private final static Charset UTF8_charset = Charset.forName("UTF8");
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final HashMap<SocketChannel, String> clientMap = new HashMap<>();


    public Server(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();
    }

    /***
     * Theses methods are here to help understanding the behavior of the selector
     ***/

//    private String interestOpsToString(SelectionKey key) {
//        if (!key.isValid()) {
//            return "CANCELLED";
//        }
//        int interestOps = key.interestOps();
//        ArrayList<String> list = new ArrayList<>();
//        if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
//            list.add("OP_ACCEPT");
//        if ((interestOps & SelectionKey.OP_READ) != 0)
//            list.add("OP_READ");
//        if ((interestOps & SelectionKey.OP_WRITE) != 0)
//            list.add("OP_WRITE");
//        return String.join("|", list);
//    }
//
//    public void printKeys() {
//        Set<SelectionKey> selectionKeySet = selector.keys();
//        if (selectionKeySet.isEmpty()) {
//            System.out.println("The selector contains no key : this should not happen!");
//            return;
//        }
//        System.out.println("The selector contains:");
//        for (SelectionKey key : selectionKeySet) {
//            SelectableChannel channel = key.channel();
//            if (channel instanceof ServerSocketChannel) {
//                System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
//            } else {
//                SocketChannel sc = (SocketChannel) channel;
//                System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
//            }
//        }
//    }
//
//    private String remoteAddressToString(SocketChannel sc) {
//        try {
//            return sc.getRemoteAddress().toString();
//        } catch (IOException e) {
//            return "???";
//        }
//    }
//
//    private void printSelectedKey() {
//        if (selectedKeys.isEmpty()) {
//            System.out.println("There were not selected keys.");
//            return;
//        }
//        System.out.println("The selected keys are :");
//        for (SelectionKey key : selectedKeys) {
//            SelectableChannel channel = key.channel();
//            if (channel instanceof ServerSocketChannel) {
//                System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
//            } else {
//                SocketChannel sc = (SocketChannel) channel;
//                System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
//            }
//        }
//    }
//
//    private String possibleActionsToString(SelectionKey key) {
//        if (!key.isValid()) {
//            return "CANCELLED";
//        }
//        ArrayList<String> list = new ArrayList<>();
//        if (key.isAcceptable())
//            list.add("ACCEPT");
//        if (key.isReadable())
//            list.add("READ");
//        if (key.isWritable())
//            list.add("WRITE");
//        return String.join(" and ", list);
//    }
    public static void main(String[] args) throws NumberFormatException, IOException {
        new Server(Integer.parseInt(args[0])).launch();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        while (!Thread.interrupted()) {
//            printKeys();
            System.out.println("Starting select");
            selector.select();
            System.out.println("Select finished");
//            printSelectedKey();
            processSelectedKeys();
            selectedKeys.clear();
        }
    }

    private void processSelectedKeys() {
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
            if (key.isValid() && key.isWritable()) {
                doWrite(key);
            }
            if (key.isValid() && key.isReadable()) {
                doRead(key);
            }
            keyIterator.remove();
        }
    }

    private void doAccept(SelectionKey key) {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        try {
            SocketChannel clientSocketChannel = serverSocketChannel.accept();
            if (clientSocketChannel == null) {
                return;
            }
            clientSocketChannel.configureBlocking(false);
            clientSocketChannel.register(selector, SelectionKey.OP_READ);
//            long startTime = System.currentTimeMillis();
//            clientTimer.put(clientSocketChannel, startTime);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void doRead(SelectionKey key) {
        SocketChannel clientSocketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFF_SIZE);
        try {
            if (null == (byteBuffer = readAll(byteBuffer, clientSocketChannel))) {
                clientSocketChannel.close();
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Client closed connection before finishing sending");
        }
        key.attach(byteBuffer);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void doWrite(SelectionKey key) {
        SocketChannel clientSocketChannel = (SocketChannel) key.channel();
        processRequest((ByteBuffer) key.attachment(), clientSocketChannel);
//        buffSend.flip();
//        try {
//            while (buffSend.hasRemaining()) {
//                clientSocketChannel.write(buffSend);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        key.interestOps(SelectionKey.OP_READ);
    }

    // Lit ce que le socketChannel reçoit et le stock dans le buffer,
    // Si le buffer est trop petit , la taille est automatiquement augmenté
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

    private void processRequest(ByteBuffer byteBuffer, SocketChannel socketChannel) {

        byteBuffer.flip();
        if (byteBuffer.remaining() < Byte.BYTES) {
            return;
        }
        byte b = byteBuffer.get();
        switch (b) {
            case E_PSEUDO:
                String pseudo = decodeE_PSEUDO(byteBuffer);
                if (pseudoAlreadyExists(pseudo)) {
                    sendAnswerPseudoExists(true, socketChannel);
                } else {
                    sendAnswerPseudoExists(false, socketChannel);
                    clientMap.put(socketChannel, pseudo);
                }


                break;
            case DC_PSEUDO:
                clientMap.remove(socketChannel);
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case D_LIST_CLIENT_CO:
                ByteBuffer bbOut = encodeE_LIST_CLIENT_CO();
                if (null == bbOut) {
                    System.err.println("This should never happen !!!!! Error : nobody is connected ");
                    return;
                }
                bbOut.flip();
                try {
                    while (bbOut.hasRemaining()) {
                        socketChannel.write(bbOut);
                    }

                } catch (IOException e) {
                    System.err.println("Client closed connection before finishing sending");
//                    e.printStackTrace();
                }

                break;
            case E_M_ALL:
//                decodeM_ALL(byteBuffer);
//                ByteBuffer tempo = encodeM_ALL();
                // Send to each socketChannel connected
                // Si socket en mode read -> attendre fin de la lecture et envoyer
                writeM_ALL(byteBuffer);
                break;
            default:
                System.err.println("Error : Unkown code " + b);
        }
        // TODO faire attention a recursivite -> ne devrait pas y avoir de soucis mais on ne sais jamais
        // // Si le client spam le serveur
        // -> analyser ce qui reste dans le bytebuffer
        if (byteBuffer.hasRemaining()) {
            processRequest(byteBuffer, socketChannel);
        }

    }

    private boolean pseudoAlreadyExists(String pseudo) {
        return clientMap.containsValue(pseudo);
    }

    private void sendAnswerPseudoExists(boolean exists, SocketChannel socketChannel) {
        ByteBuffer bbOut = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
        bbOut.put(R_PSEUDO);
        if (exists) {
            bbOut.putInt(1);
        } else {
            bbOut.putInt(0);

        }
        bbOut.flip();
        try {
            while (bbOut.hasRemaining()) {
                socketChannel.write(bbOut);
            }
        } catch (IOException e) {
            System.err.println("Error : Client closed connection before sending finished");
//            e.printStackTrace();
        }

    }


//    private List<ClientInfo> getListCo(){
//        clientMap.
//        return list;
//    }

//    private class ClientInfo {
//        private SocketChannel socketChannel;
//        private String pseudo;
//
//        public ClientInfo(SocketChannel socketChannel, String pseudo){
//            this.socketChannel = socketChannel;
//            this.pseudo = pseudo;
//        }
//
//        public SocketChannel getSocketChannel() {
//            return socketChannel;
//        }
//
//        public String getPseudo() {
//            return pseudo;
//        }
//    }

    private ByteBuffer encodeE_LIST_CLIENT_CO() {
        Long size = calculSizeBufferList();
        if (size <= 0) {
            System.err.println("This should never happen !!!!! Error : nobody is connected ");
            return null;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(size.intValue());
        byteBuffer.put(R_LIST_CLIENT_CO).putInt(clientMap.size());
        clientMap.forEach((key, value) ->
                byteBuffer.putInt(value.length())
                        .put(UTF8_charset.encode(value))
                        .putInt(key.toString().length())
                        .put(UTF8_charset.encode(key.toString()))
        );
        return byteBuffer;
    }

    private long calculSizeBufferList() {
        final Long[] total = {0L};
        total[0] += Byte.BYTES;
        total[0] += clientMap.size();
        clientMap.forEach((key, value) -> { // Peut se simplifier mais cette forme est plus clair
            long clientSize = Integer.BYTES + value.length() + Integer.BYTES + key.toString().length();
            total[0] += clientSize;
        });
        return total[0];
    }


    private String decodeE_PSEUDO(ByteBuffer byteBuffer) {
        // TODO

        if (byteBuffer.remaining() < Integer.BYTES) {
            System.err.println("Missing size of name");
            return null;
        }
        int size = byteBuffer.getInt();
        if (byteBuffer.remaining() < size) {
            System.err.println("The message is incomplete");
            return null;
        }
        ByteBuffer tempo = ByteBuffer.allocate(size);
        for (int i = 0; i < size; i++) {
            tempo.put(byteBuffer.get());
        }
        return UTF8_charset.decode(tempo).toString();

    }

    private void writeM_ALL(ByteBuffer byteBuffer) {
        int sizeName = byteBuffer.getInt();
        ByteBuffer name = ByteBuffer.allocate(sizeName);
        for (int i = 0; i < sizeName; i++) {
            name.put(byteBuffer.get());
        }
        int sizeMessage = byteBuffer.getInt();
        ByteBuffer message = ByteBuffer.allocate(sizeName);
        for (int i = 0; i < sizeName; i++) {
            message.put(byteBuffer.get());
        }

        ByteBuffer toSend = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + sizeName + Integer.BYTES + sizeMessage);
        toSend.put(E_M_ALL)
                .putInt(sizeName)
                .put(name)
                .putInt(sizeMessage)
                .put(message);
        writeOneMessageToAll(toSend);
    }

    private void writeOneMessageToAll(ByteBuffer byteBuffer) {
        // TODO
        // Peut etre avoir besoin de savoir si la channel est en read ou write
        ByteBuffer bbOut = byteBuffer.duplicate();
        bbOut.flip();
        clientMap.forEach((key, value) -> {
            try {
                System.out.println("DEBUG : bbOut = " + bbOut);
                while (bbOut.hasRemaining()) {
                    key.write(bbOut);
                }

            } catch (IOException e) {
                System.err.println("Could not write on channel");
                e.printStackTrace();
            }
            bbOut.rewind(); // Remet la position au début pour une réutilisation
        });
    }


}

