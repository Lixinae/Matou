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

    private final static int BUFF_SIZE = 1024;

    /* Codes pour la reception de paquets */
    private final static byte E_PSEUDO = 1;
    private final static byte D_PSEUDO = 6;
    private final static byte D_LIST_CLIENT_CO = 7;

    /* Code pour l'envoie de paquets */
    private final static byte E_LIST_CLIENT_CO = 8;

    /* Concerne l'envoie et la reception */
    private final static byte M_ALL = 9;

//    private final static int TIME_OUT = 1000;

    //    private final Map<SocketChannel, Long> clientTimer = new HashMap<>();
//    private final static byte R_LIST_CLIENT_CO = 8;

    private final static Charset UTF8_charset = Charset.forName("UTF8");
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final HashMap<SocketChannel, String> map = new HashMap<>();


    public Server(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();
    }

    /***
     * Theses methods are here to help understanding the behavior of the selector
     ***/
/*
    private String interestOpsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        int interestOps = key.interestOps();
        ArrayList<String> list = new ArrayList<>();
        if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
            list.add("OP_ACCEPT");
        if ((interestOps & SelectionKey.OP_READ) != 0)
            list.add("OP_READ");
        if ((interestOps & SelectionKey.OP_WRITE) != 0)
            list.add("OP_WRITE");
        return String.join("|", list);
    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out.println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
            }
        }
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e) {
            return "???";
        }
    }

    private void printSelectedKey() {
        if (selectedKeys.isEmpty()) {
            System.out.println("There were not selected keys.");
            return;
        }
        System.out.println("The selected keys are :");
        for (SelectionKey key : selectedKeys) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
            }
        }
    }

    private String possibleActionsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        ArrayList<String> list = new ArrayList<>();
        if (key.isAcceptable())
            list.add("ACCEPT");
        if (key.isReadable())
            list.add("READ");
        if (key.isWritable())
            list.add("WRITE");
        return String.join(" and ", list);
    }
*/
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

//        ByteBuffer byteBuffer = ByteBuffer.allocate(BUFF_SIZE);

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

        if (byteBuffer.remaining() < Byte.BYTES) {
            return;
        }
        byte b = byteBuffer.get();
        switch (b) {
            case E_PSEUDO:
                String pseudo = decodeE_PSEUDO(byteBuffer);
                map.put(socketChannel, pseudo);
//                list.add(clientInfo);
                break;
            case D_PSEUDO:
                map.remove(socketChannel);
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case D_LIST_CLIENT_CO:


                break;
            case M_ALL:
//                decodeM_ALL(byteBuffer);
//                ByteBuffer tempo = encodeM_ALL();
                // Send to each socketChannel connected
                // Si socket en mode read -> attendre fin de la lecture et envoyer

                break;
            default:
                System.err.println("Error : Unkown code " + b);
        }

    }


//    private List<ClientInfo> getListCo(){
//        map.
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
        // TODO -> Change valeur 10
        ByteBuffer byteBuffer = ByteBuffer.allocate(10);

        return byteBuffer;
    }

    private String decodeE_PSEUDO(ByteBuffer byteBuffer) {
        // TODO
        if (byteBuffer.remaining() < Integer.BYTES) {
            System.err.println("Missing size of name");
            return null;
        }
        return UTF8_charset.decode(byteBuffer).toString();
//                if(size >= byteBuffer.remaining()){
//                    System.err.println("Wrong size of");
//                    return;
//                }
    }

    private void writeM_ALL(ByteBuffer byteBuffer) {
//        for (Map. entry : map.keySet()) {
//            ByteBuffer bbOut = byteBuffer.duplicate();
//            SocketChannel sc = entry.getKey();
//
//
//            try {
//                sc.write(bbOut);
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        // TODO
        // Peut etre avoir besoin de savoir si la channel est en read ou write
        map.forEach((key, value) -> {
            ByteBuffer bbOut = byteBuffer.duplicate();
            try {
                key.write(bbOut);
            } catch (IOException e) {
                System.err.println("Could not write on channel");
                e.printStackTrace();
            }
            byteBuffer.rewind(); // Remet la position au début pour une réutilisation
        });

    }
//    private List<String> decodeM_ALL(ByteBuffer byteBuffer) {
//        // TODO
//        List<String> list = new ArrayList<>();
//
//
//
//        return null;
//    }
//
//    private ByteBuffer encodeM_ALL(String s) {
//        // TODO
//        ByteBuffer byteBuffer = UTF8_charset.encode(s);
//
//
//        return byteBuffer;
//    }
}

