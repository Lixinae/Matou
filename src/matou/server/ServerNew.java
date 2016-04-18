package matou.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * Project :Matou
 * Created by Narex on 09/03/2016.
 */
public class ServerNew {

    private final static int BUFF_SIZE = 1024;

    private final static int MAX_SIZE = 1073741824; // Taille maximal d'un buffer (donc MAX_SIZE octet entrant au maximum)
    /* Recuperation du pseudo que le client envoie */
    private final static byte E_PSEUDO = 1;
    /* Demande d'une connection d'un client à un autre */
    private final static byte CO_CLIENT_TO_CLIENT = 2;
    /* Demande de deconnection d'un client */
    private final static byte DC_PSEUDO = 6;

    /*                                    */
    /* Codes pour la reception de paquets */
    /*                                    */
    /* Demande la liste des clients */
    private final static byte D_LIST_CLIENT_CO = 7;
    /* Demande d'envoie à tous */
    private final static byte E_M_ALL = 9;
    /* Reception de l'adresse server du client */
    private static final byte E_ADDR_SERV_CLIENT = 11;
    /* Reponse a la demande de la liste des clients*/
    private final static byte R_LIST_CLIENT_CO = 8;
    /* Reponse du serveur au client sur la disponibilite de son pseudo*/
    private final static byte R_PSEUDO = 10;
    private final static Charset UTF8_charset = Charset.forName("UTF8");

    /*                               */
    /* Code pour l'envoie de paquets */
    /*                               */
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final HashMap<SocketChannel, ClientInfo> clientMap = new HashMap<>();

    public ServerNew(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
        serverSocketChannel.bind(inetSocketAddress);
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();
//        requestProcessor = new RequestProcessor();
    }

    private static void usage() {
        System.out.println("java matou.server.Server 7777");
    }

//    private final RequestProcessor requestProcessor;

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        new ServerNew(Integer.parseInt(args[0])).launch();
    }

    public void launch() throws IOException {
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        System.out.println("Server started on " + serverSocketChannel.getLocalAddress());
        while (!Thread.interrupted()) {
            printKeys();
            System.out.println("Starting select");
            selector.select();
            System.out.println("Select finished");
            printSelectedKey();
            processSelectedKeys();
            selectedKeys.clear();
        }
    }

    private void processSelectedKeys() throws IOException {
        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
        while (keyIterator.hasNext()) {
            SelectionKey key = keyIterator.next();
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
            try {
                if (key.isValid() && key.isWritable()) {
                    doWrite(key);
                }
                if (key.isValid() && key.isReadable()) {
                    doRead(key);
                }
            } catch (IOException e) {

            }
            keyIterator.remove();
        }
//        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
//        while (keyIterator.hasNext()) {
//            SelectionKey key = keyIterator.next();
//            if (key.isValid() && key.isAcceptable()) {
//                doAccept(key);
//            }
//            try {
//                if (key.isValid() && key.isWritable()) {
//                    doWrite(key);
//                }
//                if (key.isValid() && key.isReadable()) {
//                    doRead(key);
//                }
//            } catch (IOException ie) {
//
//            }
//            keyIterator.remove();
//        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientSocketChannel = serverSocketChannel.accept();
        if (clientSocketChannel == null) {
            return;
        }
        System.out.println("Client connected " + clientSocketChannel);
        clientSocketChannel.configureBlocking(false);
        clientSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new ClientInfo(clientSocketChannel));
    }

    // Pas de readAll en non bloquant
    private void doRead(SelectionKey key) throws IOException {
        System.out.println("In READ");
        ClientInfo clientInfo = (ClientInfo) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        if (-1 == client.read(clientInfo.in)) {
            System.out.println("///////////////////////Closed///////////////////////");
            clientInfo.isClosed = true;
            if (clientInfo.in.position() == 0) {
                clientMap.remove(client);
                client.close();
                return;
            }
        }
        clientInfo.buildOutBuffer();
        key.interestOps(clientInfo.getInterestKey());
    }

    private void doWrite(SelectionKey key) throws IOException {
        System.out.println("In WRITE");
        ClientInfo clientInfo = (ClientInfo) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        //requestProcessor.processRequest(key);

        System.err.println(clientInfo);
        if (clientInfo.status == CurrentStatus.END) {
            System.out.println("Before send");
            System.out.println(clientInfo.out);
            clientInfo.out.flip();
            client.write(clientInfo.out);
            clientInfo.out.compact();
            if (clientInfo.isClosed) {
                clientMap.remove(client);
                client.close();
//                clientInfo.isClosed = true;
                return;
            }
            clientInfo.status = CurrentStatus.BEGIN;
            System.out.println("status = " + clientInfo.status);
        }
        key.interestOps(clientInfo.getInterestKey());
    }

    private void cleanMapFromInvalidElements() {

        clientMap.keySet().removeIf(e -> remoteAddressToString(e).equals("???"));
//        clientMapServer.keySet().removeIf(s -> !clientMap.containsKey(s));
//
//        if (clientMapServer.size() > 0 && clientMap.size() > 0) {
//            if (clientMapServer.size() != clientMap.size()) {
//                System.err.println("Map de taille différentes");
//            }
//        }
    }

    /***
     * Theses methods are here to help understanding the behavior of the
     * selector
     ***/

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

    // Lit ce que le socketChannel reçoit et le stock dans le buffer,
    // Si le buffer est trop petit , la taille est automatiquement augmenté
    // jusqu'a ce qu'il ne soit plus plein
//    private ByteBuffer readAll(ByteBuffer bbIn, SocketChannel sc) throws IOException {
//        while (sc.read(bbIn) != -1) {
//            if (bbIn.position() < bbIn.limit()) {
//                return bbIn;
//            }
//            bbIn.flip();
//            ByteBuffer tempo = bbIn.duplicate();
//            int nextSize = tempo.capacity() * 2;
//            if (nextSize < MAX_SIZE) {
//                bbIn = ByteBuffer.allocateDirect(nextSize);
//            } else {
//                System.err.println("This should never happen if the client is correctly implemented !!! Error : nextSize > MAX_size ");
//                System.err.println("The bytebuffer out is incomplete");
//            }
//            bbIn.put(tempo);
//            if (!bbIn.hasRemaining()) {
//                return bbIn;
//            }
//        }
//        return null;
//    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out
                    .println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : "
                        + interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client "
                        + remoteAddressToString(sc) + " : "
                        + interestOpsToString(key));
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
                System.out.println("\tServerSocketChannel can perform : "
                        + possibleActionsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tClient " + remoteAddressToString(sc)
                        + " can perform : " + possibleActionsToString(key));
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

    private enum CurrentStatus {
        BEGIN(0),
        MIDDLE(1),
        END(2);

        private final byte value;

        CurrentStatus(int value) {
            this.value = (byte) value;
        }

        public byte getValue() {
            return value;
        }
    }

    // Reponse du serveur au client à propos de son pseudo
    private enum answerPseudo {
        TAKEN(0),
        FREE(1),
        ALREADY_CHOSEN(2);

        private final byte value;

        answerPseudo(int value) {
            this.value = (byte) value;
        }

        public byte getValue() {
            return value;
        }
    }

    private class ClientInfo {
        boolean isClosed = false;
        CurrentStatus status = CurrentStatus.BEGIN;
        private String name = null;
        private ByteBuffer in;
        private ByteBuffer out;
        private SocketChannel socketChannel;
        private byte currentOp = -1;
        private InetSocketAddress adressServer;


        public ClientInfo(SocketChannel socketChannel) {
            in = ByteBuffer.allocate(BUFF_SIZE);
            out = ByteBuffer.allocate(BUFF_SIZE);
            this.socketChannel = socketChannel;
        }

        public void buildOutBuffer() {
            cleanMapFromInvalidElements();

            System.out.println("Status = " + status);
//            System.out.println("in = " + in);
            // Lis le 1er byte et le stocke
            if (in.position() > 0 && status == CurrentStatus.BEGIN) {
                in.flip();
                currentOp = in.get();
                System.out.println("CurrentOp = " + currentOp);
                status = CurrentStatus.MIDDLE;
                in.compact();
                //return;
            }
//            System.out.println("status after begin = " + status);
//            System.out.println("In = " + in);

            // Une fois qu'on a lu le 1er byte on passe à la suite
            // Dès qu'une opération est effectué , le status passe à "END" , ce qui permettra d'écrire la réponse au client
            if (status == CurrentStatus.MIDDLE) {
                in.flip();
                switch (currentOp) {
                    case E_PSEUDO:
                        System.out.println("Entering decode pseudo");
                        decodeE_PSEUDO();
                        System.out.println("Exiting decode pseudo");
                        break;
                    case CO_CLIENT_TO_CLIENT:
                        System.out.println("Entering co client");
                        // TODO
                        //decodeCO_CLIENT_TO_CLIENT(byteBuffer);
                        System.out.println("Exiting co client");
                        break;
                    case DC_PSEUDO:
                        System.out.println("Entering disconnection");
                        // TODO verifer le fonctionnement
                        decodeDC_PSEUDO();
                        System.out.println("Exiting disconnection");
                        break;
                    case D_LIST_CLIENT_CO:
                        System.out.println("Entering demand list client");
                        // TODO
                        decodeD_LIST_CLIENT_CO();
                        System.out.println("Exiting demand list client");
                        break;
                    case E_M_ALL:
                        System.out.println("Entering envoie message all");
                        // TODO : a voir comment regler le soucis de l'envoie à tous
                        //
//                        decodeM_ALL(byteBuffer, socketChannel);
                        System.out.println("Exiting envoie message all");
                        break;
                    case E_ADDR_SERV_CLIENT:
                        System.out.println("Entering envoie adresse serveur client");
                        // TODO
                        decodeE_ADDR_SERV_CLIENT();
                        System.out.println("Exiting envoie adresse serveur client");
                        break;

                    default:
                        System.err.println("Error : Unkown code " + currentOp);
                        break;
                }
            }
        }

        // Fonctionne
        private void decodeE_PSEUDO() {
            int size = -1;
            if (in.remaining() < Integer.BYTES) {
                in.compact();
                return;
            }
            size = in.getInt();
            if (in.remaining() < size) {
                in.compact();
                return;
            }
            ByteBuffer tempo = ByteBuffer.allocate(size);
            for (int i = 0; i < size; i++) {
                tempo.put(in.get());
            }
            tempo.flip();
            String pseudo = UTF8_charset.decode(tempo).toString();
            System.out.println("Pseudo = " + pseudo);


            in.compact();
            //// Ecriture de la reponse dans le buffer de sortie ////
            out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
            out.put(R_PSEUDO);
            if (pseudoAlreadyExists(pseudo)) {
                // ordinal -> valeur du TAKEN de answerPseudo
                // = 0
                System.out.println("Pseudo exists");
                out.putInt(answerPseudo.TAKEN.getValue());
            } else {
                // Si le client a deja un nom , il ne peut pas en changer
                if (name != null) {
                    // Indique au client qu'il a déjà choisi son pseudo
                    // = 2
                    System.out.println("Pseudo already chosen");
                    out.putInt(answerPseudo.ALREADY_CHOSEN.getValue());
                    status = CurrentStatus.END;
                    return;
                }
                System.out.println("Pseudo free");
                // Valeur de FREE = 1
                out.putInt(answerPseudo.FREE.getValue());
                // On n'attribut le nom que s'il est libre
                name = pseudo;
                // On ajoute le pseudo du client à la map des clients


                clientMap.put(socketChannel, this);

            }
            status = CurrentStatus.END;
            System.out.println("status end = " + status);
        }

        private boolean pseudoAlreadyExists(String pseudo) {
            final boolean[] exists = {false};
            clientMap.forEach((sc, clientInfo) -> {
                if (clientInfo.getName().equals(pseudo)) {
                    exists[0] = true;
                }
            });
            return exists[0];
        }

        private void decodeDC_PSEUDO() {
            SocketChannel tmp = socketChannel;
            System.out.println("Disconnecting client : " + clientMap.get(tmp));
            // On remet à begin parce qu'il n'y a rien a écrire dans ce cas.
            status = CurrentStatus.BEGIN;
            clientMap.remove(tmp);
        }

        // TODO
        private void decodeD_LIST_CLIENT_CO() {
            encodeR_LIST_CLIENT_CO();
            in.compact();
            status = CurrentStatus.END;
        }

        private void encodeR_LIST_CLIENT_CO() {
            Long size = calculSizeBufferList();
            if (size <= 0) {
                System.err.println("This should never happen !!!!! Error : nobody is connected ");
                return;
            }
            out = ByteBuffer.allocate(size.intValue());
            out.put(R_LIST_CLIENT_CO)
                    .putInt(clientMap.size());

            StringBuilder sb = new StringBuilder();
            clientMap.forEach((socketChannel, clientInfo) -> {
                        sb.delete(0, sb.length());
                        sb.append(clientInfo.getAdressServer().getHostString())
                                .append(":")
                                .append(clientInfo.getAdressServer().getPort());

                        String to_encode = sb.toString();
                        out.putInt(clientInfo.getName().length())
                                .put(UTF8_charset.encode(clientInfo.getName()))
                                .putInt(sb.length())
                                .put(UTF8_charset.encode(to_encode));
                    }
            );
        }

        private long calculSizeBufferList() {
            final Long[] total = {0L};
            total[0] += Byte.BYTES;
            total[0] += Integer.BYTES;
            clientMap.forEach((key, clientInfo) -> {
                // Peut se simplifier mais cette forme est plus clair
                long clientSize = Integer.BYTES + clientInfo.getName().length() + Integer.BYTES + clientInfo.getAdressServer().toString().length();
                total[0] += clientSize;
            });
            return total[0];
        }


        // TODO
        private void decodeE_ADDR_SERV_CLIENT() {
            if (in.remaining() < Integer.BYTES) {
                in.compact();
                return;
            }
            int size = in.getInt();
            if (in.remaining() < size) {
                in.compact();
                return;
            }
            ByteBuffer tempo = ByteBuffer.allocate(size);
            for (int i = 0; i < size; i++) {
                tempo.put(in.get());
            }
            tempo.flip();
            in.compact();
            if (adressServer == null) {
                // On remet à begin parce qu'il n'y a rien a écrire dans ce cas.
                status = CurrentStatus.BEGIN;
                String fullChain = UTF8_charset.decode(tempo).toString();
                int splitIndex = fullChain.lastIndexOf(':');
                String host = fullChain.substring(0, splitIndex);
                int port = Integer.parseInt(fullChain.substring(splitIndex + 1));
                adressServer = new InetSocketAddress(host, port);

                clientMap.values().forEach(System.out::println);
            }
        }

        public int getInterestKey() {
            int interestKey = 0;// initialize
            if (out.position() > 0) {
                interestKey = interestKey | SelectionKey.OP_WRITE;
            }
            if (!isClosed) {
                interestKey = interestKey | SelectionKey.OP_READ;
            }
            return interestKey;
        }

        public String getName() {
            return name;
        }

        public InetSocketAddress getAdressServer() {
            return adressServer;
        }

        @Override
        public String toString() {
            return "ClientInfo{" +
                    "   \nisClosed=" + isClosed +
                    "   \nstatus=" + status +
                    "   \nname='" + name + '\'' +
                    "   \nin=" + in +
                    "   \nout=" + out +
                    "   \nsocketChannel=" + socketChannel +
                    "   \ncurrentOp=" + currentOp +
                    "   \nadressServer=" + adressServer +
                    "\n}\n";
        }

    }
}

