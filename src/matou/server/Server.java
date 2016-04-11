package matou.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Project :Matou
 * Created by Narex on 09/03/2016.
 */
public class Server {

    private final static int BUFF_SIZE = 8;

    private final static int MAX_SIZE = 1073741824; // Taille maximal d'un buffer (donc MAX_SIZE octet entrant au maximum)

    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;

    private final RequestProcessor requestProcessor;


    public Server(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
        System.out.println(inetSocketAddress.getHostString());
        System.out.println(inetSocketAddress.getPort());
        System.out.println(inetSocketAddress);
        serverSocketChannel.bind(inetSocketAddress);
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();
        requestProcessor = new RequestProcessor();


    }

    private static void usage() {
        System.out.println("java matou.server.Server 7777");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new Server(Integer.parseInt(args[0])).launch();
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
//                System.out.println("\tKey for matou.client.Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
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
//                System.out.println("\tmatou.client.Client " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
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
    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        System.out.println("Server started on " + serverSocketChannel.getLocalAddress());
        while (!Thread.interrupted()) {
//            printKeys();
//            System.out.println("Starting select");
            selector.select();
//            System.out.println("Select finished");
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
        requestProcessor.processRequest(key);
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
            int nextSize = tempo.capacity() * 2;
            if (nextSize < MAX_SIZE) {
                bbIn = ByteBuffer.allocateDirect(nextSize);
            } else {
                System.err.println("This should never happen if the client is correctly implemented !!! nextSize > MAX_size -> error ");
                System.err.println("The bytebuffer out is incomplete");
            }
            bbIn.put(tempo);
            if (!bbIn.hasRemaining()) {
                return bbIn;
            }
        }
        return null;
    }
}

