package matou.server;

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

/**
 * Project :Matou
 * Created by Narex on 09/03/2016.
 */
public class ServerNew {

    private final static int BUFF_SIZE = 8;

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
    private final HashMap<SocketChannel, ClientInfo> clientInfoMap = new HashMap<>();

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
            selector.select();
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
            } catch (IOException ie) {

            }
            keyIterator.remove();
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientSocketChannel = serverSocketChannel.accept();
        if (clientSocketChannel == null) {
            return;
        }
        clientSocketChannel.configureBlocking(false);
        clientSocketChannel.register(selector, SelectionKey.OP_READ);
    }

    // Pas de readAll en non bloquant
    private void doRead(SelectionKey key) throws IOException {
        ClientInfo clientInfo = (ClientInfo) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        if (-1 == client.read(clientInfo.in)) {
            clientInfo.isClosed = true;
            if (clientInfo.in.position() == 0) {
                client.close();
            }
        }
        clientInfo.bOut();
        int interestKey;
        if ((interestKey = clientInfo.getInterestKey()) != 0) {
            key.interestOps(interestKey);
        }
    }

    private void doWrite(SelectionKey key) throws IOException {
        ClientInfo clientInfo = (ClientInfo) key.attachment();
        SocketChannel client = (SocketChannel) key.channel();
        //requestProcessor.processRequest(key);
        if (clientInfo.status == CurrentStatus.END) {
            clientInfo.out.flip();
            client.write(clientInfo.out);
            clientInfo.out.compact();
            if (clientInfo.isClosed) {
                client.close();
                clientInfo.isClosed = true;
            }
            clientInfo.status = CurrentStatus.BEGIN;
        }
        key.interestOps(clientInfo.getInterestKey());
    }


    private enum CurrentStatus {
        BEGIN, MIDDLE, END
    }

    // Reponse du serveur au client à propos de son pseudo
    private enum answerPseudo {
        FREE,
        TAKEN,
        ALREADY_CHOSEN
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

    private class ClientInfo {
        boolean isClosed;
        CurrentStatus status = CurrentStatus.BEGIN;
        private String name = null;
        private ByteBuffer in;
        private ByteBuffer out;
        private SelectionKey key;
        private byte currentOp = -1;


        public ClientInfo(SelectionKey key) {
            in = ByteBuffer.allocate(BUFF_SIZE);
            out = ByteBuffer.allocate(BUFF_SIZE);
            this.key = key;
        }

        public void bOut() {

            // Lis le 1er byte et le stocke
            if (in.remaining() > 0 && status == CurrentStatus.BEGIN) {
                in.flip();
                currentOp = in.get();
                status = CurrentStatus.MIDDLE;
                in.compact();
                return;
            }
            // Une fois qu'on a lu le 1er byte on passe à la suite
            // Dès qu'une opération est effectué , le status passe à "END" , ce qui permettra d'écrire la réponse au client
            if (status == CurrentStatus.MIDDLE) {
                switch (currentOp) {
                    case E_PSEUDO:
                        System.out.println("Entering decode pseudo");
                        decodeE_PSEUDO();
                        System.out.println("Exiting decode pseudo");
                        break;
                    case CO_CLIENT_TO_CLIENT:
                        System.out.println("Entering co client");
                        //decodeCO_CLIENT_TO_CLIENT(byteBuffer);
                        System.out.println("Exiting co client");
                        break;
                    case DC_PSEUDO:
                        System.out.println("Entering disconnection");
                        //dc = decodeDC_PSEUDO(key, socketChannel);
                        System.out.println("Exiting disconnection");
                        break;
                    case D_LIST_CLIENT_CO:
                        System.out.println("Entering demand list client");
//                        if (decodeD_LIST_CLIENT_CO(socketChannel)) {
//                            // Enorme erreur si on arrive ici
//                            return;
//                        }
                        System.out.println("Exiting demand list client");
                        break;
                    case E_M_ALL:
                        System.out.println("Entering envoie message all");
//                        decodeM_ALL(byteBuffer, socketChannel);
                        System.out.println("Exiting envoie message all");
                        break;
                    case E_ADDR_SERV_CLIENT:
                        System.out.println("Entering envoie adresse serveur client");
//                        decodeE_ADDR_SERV_CLIENT(byteBuffer, socketChannel);
                        System.out.println("Exiting envoie adresse serveur client");
                        break;

                    default:
                        System.err.println("Error : Unkown code " + currentOp);
                        break;
                }
            }


        }

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


            // Reponse
            out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);

            if (pseudoAlreadyExists(pseudo)) {
                // ordinal -> valeur du TAKEN de answerPseudo
                // = 1
                out.putInt(answerPseudo.TAKEN.ordinal());
            } else {
                // Si le client a deja un nom , il ne peut pas en changer
                if (name != null) {
                    // Indique au client qu'il a déjà choisi son pseudo
                    // = 2
                    out.putInt(answerPseudo.ALREADY_CHOSEN.ordinal());
                    status = CurrentStatus.END;
                    return;
                }

                // = 0
                out.putInt(answerPseudo.FREE.ordinal());
                // On n'attribut le nom que s'il est libre
                name = pseudo;
                SocketChannel socket = (SocketChannel) key.channel();
                // J'ai des doutes sur cette forme
                clientInfoMap.put(socket, this);
            }
            status = CurrentStatus.END;
        }

        // TODO
        private boolean pseudoAlreadyExists(String pseudo) {
            final boolean[] exists = {false};
            clientInfoMap.forEach((socketChannel, clientInfo) -> {
                if (clientInfo.name.equals(pseudo)) {
                    exists[0] = true;
                }
            });
            return exists[0];
        }


        public int getInterestKey() {
            int interestKey = 0;// initialize
            if (out.position() > 0) {
                interestKey = interestKey | SelectionKey.OP_WRITE;
            }
            if (!isClosed && in.hasRemaining()) {
                interestKey = interestKey | SelectionKey.OP_READ;
            }
            return interestKey;
        }

    }
}

