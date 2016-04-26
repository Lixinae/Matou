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
public class Server {

    private final static int BUFF_SIZE = 1024;

    private final Charset UTF8_charset = Charset.forName("UTF8");
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final HashMap<SocketChannel, ClientInfo> clientMap = new HashMap<>();

    public Server(int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(port);
        serverSocketChannel.bind(inetSocketAddress);
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();
    }

    private static void usage() {
        System.out.println("java matou.server.Server 7777");
    }

    /**
     * @param args Argument
     * @throws IOException S'il y a une IO Exception
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("No arguments giving\nStarting Server with default values\nport = 7777");
            new Server(Integer.parseInt("7777")).launch();
        } else {
            if (args.length != 1) {
                usage();
                return;
            }
            new Server(Integer.parseInt(args[0])).launch();
        }

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
                ClientInfo clientInfo = (ClientInfo) key.attachment();
                if (key.isValid() && key.isWritable()) {
                    clientInfo.doWrite(key);
                }
                if (key.isValid() && key.isReadable()) {
                    clientInfo.doRead(key);
                }
            } catch (IOException e) {

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
        System.out.println("Client connected " + clientSocketChannel);
        clientSocketChannel.configureBlocking(false);
        clientSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, new ClientInfo(clientSocketChannel));
    }

    private void cleanMapFromInvalidElements() {
        clientMap.keySet().removeIf(e -> remoteAddressToString(e).equals("???"));
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e) {
            return "???";
        }
    }

    private enum PacketType {
        E_PSEUDO(1),
        E_CO_CLIENT_TO_CLIENT(2),
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
        private SelectionKey selectionKey;


        public ClientInfo(SocketChannel socketChannel) {
            in = ByteBuffer.allocate(BUFF_SIZE);
            out = ByteBuffer.allocate(BUFF_SIZE);
            this.socketChannel = socketChannel;
        }

        /**
         * Construit le buffer de sortie
         *
         * @param key la clé à mettre à jour dans la classe interne
         */
        public void buildOutBuffer(SelectionKey key) {
            this.selectionKey = key;

            cleanMapFromInvalidElements();

            // Lis le 1er byte et le stocke
            if (in.position() > 0 && status == CurrentStatus.BEGIN) {
                in.flip();
                currentOp = in.get();
                System.out.println("CurrentOp = " + currentOp);
                status = CurrentStatus.MIDDLE;
                in.compact();
                in.flip();
            }

            // Une fois qu'on a lu le 1er byte on passe à la suite
            // Dès qu'une opération est effectué , le status passe à "END" , ce qui permettra d'écrire la réponse au client
            if (status == CurrentStatus.MIDDLE) {
                // reset position au 1er bit lu si jamais on reboucle , pour pouvoir tout relire sauf le 1er bit deja lu
                in.position(0);
                PacketType bb2 = PacketType.encode(currentOp);
                switch (bb2) {
                    case E_PSEUDO:
                        System.out.println("Entering decode pseudo");
                        decodeE_PSEUDO();
                        System.out.println("Exiting decode pseudo");
                        break;
                    case E_CO_CLIENT_TO_CLIENT:
                        System.out.println("Entering co client");
                        decodeCO_CLIENT_TO_CLIENT();
                        System.out.println("Exiting co client");
                        break;
                    case DC_PSEUDO:
                        System.out.println("Entering disconnection");
                        decodeDC_PSEUDO();
                        System.out.println("Exiting disconnection");
                        break;
                    case D_LIST_CLIENT_CO:
                        System.out.println("Entering demand list client");
                        decodeD_LIST_CLIENT_CO();
                        System.out.println("Exiting demand list client");
                        break;
                    case E_M_ALL:
                        System.out.println("Entering envoie message all");
                        decodeM_ALL();
                        System.out.println("Exiting envoie message all");
                        break;
                    case E_ADDR_SERV_CLIENT:
                        System.out.println("Entering envoie adresse serveur client");
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
            int size;
            if (in.remaining() < Integer.BYTES) {
//                in.compact();
                return;
            }
            size = in.getInt();
            if (in.remaining() < size) {
//                in.position(in.position() - Integer.BYTES);
//                in.compact();
                return;
            }
            ByteBuffer tempo = ByteBuffer.allocate(size);
            for (int i = 0; i < size; i++) {
                tempo.put(in.get());
            }
            tempo.flip();
            String pseudo = UTF8_charset.decode(tempo).toString();
//            System.out.println("Pseudo = " + pseudo);


            in.compact();
            //// Ecriture de la reponse dans le buffer de sortie ////
            if (writeOutAnswerPseudo(pseudo)) {
                return;
            }
            status = CurrentStatus.END;
//            System.out.println("status end = " + status);
        }

        private boolean writeOutAnswerPseudo(String pseudo) {
            out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
            out.put(PacketType.R_PSEUDO.getValue());
            if (pseudoAlreadyExists(pseudo)) {
                out.putInt(answerPseudo.TAKEN.getValue());
            } else {
                // Si le client a deja un nom , il ne peut pas en changer
                if (name != null) {
                    out.putInt(answerPseudo.ALREADY_CHOSEN.getValue());
                    status = CurrentStatus.END;
                    return true;
                }
                // Valeur de FREE = 1
                out.putInt(answerPseudo.FREE.getValue());
                // On n'attribut le nom que s'il est libre
                name = pseudo;
                // On ajoute le pseudo du client à la map des clients


                clientMap.put(socketChannel, this);

            }
            return false;
        }

        private boolean pseudoAlreadyExists(String pseudo) {
            final boolean[] exists = {false};
            clientMap.forEach((sc, clientInfo) -> {
                if (clientInfo.getName().compareToIgnoreCase(pseudo) == 0) {
                    exists[0] = true;
                }
            });
            return exists[0];
        }

        private void decodeCO_CLIENT_TO_CLIENT() {
            if (in.remaining() < Integer.BYTES) {
                System.err.println(" E_CO_CLIENT_TO_CLIENT in < Int");
                return;
            }
            int sizeDestinataire = in.getInt();
            if (in.remaining() < sizeDestinataire) {
                System.err.println(" E_CO_CLIENT_TO_CLIENT in < Dest");
                return;
            }
            ByteBuffer destBuff = ByteBuffer.allocate(sizeDestinataire);
            for (int i = 0; i < sizeDestinataire; i++) {
                destBuff.put(in.get());
            }

            if (in.remaining() < Integer.BYTES) {
                System.err.println(" E_CO_CLIENT_TO_CLIENT dest lu mais -> in < Int");
                return;
            }


            int sizeSrc = in.getInt();
            if (in.remaining() < sizeSrc) {
                System.err.println(" E_CO_CLIENT_TO_CLIENT dest lu mais -> in < SRC");
                return;
            }
            ByteBuffer srcBuff = ByteBuffer.allocate(sizeSrc);
            for (int i = 0; i < sizeSrc; i++) {
                srcBuff.put(in.get());
            }
            srcBuff.flip();
            destBuff.flip();
            String dest = UTF8_charset.decode(destBuff).toString();


            // TODO Peut etre des changement à faire ici
            if (!findClientInMap(dest)) {
                in.compact();
                out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
                out.put(PacketType.R_CO_CLIENT_TO_CLIENT.getValue())// -1 -> placeholder
                        .putInt(0);

                status = CurrentStatus.END;

            } else {
                // Ne touche que au buffer Out du destinataire
                clientMap.forEach((sc, clientInfo) -> {
                    if (!sc.equals(socketChannel)) {
                        if (dest.equals(clientInfo.getName())) {
                            clientInfo.out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + sizeSrc);
                            clientInfo.out.put(PacketType.E_CO_CLIENT_TO_CLIENT.getValue())
                                    .putInt(sizeSrc)
                                    .put(srcBuff);
                            clientInfo.status = CurrentStatus.END;
                            clientInfo.selectionKey.interestOps(SelectionKey.OP_WRITE);
                        }
                    }
                });


                out = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES);
                out.put(PacketType.R_CO_CLIENT_TO_CLIENT.getValue())// -1 -> placeholder
                        .putInt(1);

                status = CurrentStatus.END;
                in.compact();
            }
        }

        private boolean findClientInMap(String dest) {
            final boolean[] toReturn = {false};
            clientMap.values().forEach((clientInfo) -> {
                if (dest.equals(clientInfo.getName())) {
                    toReturn[0] = true;
                }
            });
            return toReturn[0];
        }

        private void decodeDC_PSEUDO() {
            SocketChannel tmp = socketChannel;
            System.out.println("Disconnecting client : " + clientMap.get(tmp));
            // On remet à begin parce qu'il n'y a rien a écrire dans ce cas.
            status = CurrentStatus.BEGIN;
            clientMap.remove(tmp);
        }

        // Fonctionne
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
            out.put(PacketType.R_LIST_CLIENT_CO.getValue())
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
            clientMap.values().forEach((clientInfo) -> {
                // Peut se simplifier mais cette forme est plus clair
                long clientSize = Integer.BYTES + clientInfo.getName().length() + Integer.BYTES + clientInfo.getAdressServer().toString().replace("/", "").length();
                total[0] += clientSize;
            });
            return total[0];
        }

        // Fonctionne
        private void decodeE_ADDR_SERV_CLIENT() {
            if (in.remaining() < Integer.BYTES) {
//                in.compact();
                return;
            }
            int size = in.getInt();
            if (in.remaining() < size) {
//                in.position(in.position() - Integer.BYTES);
//                in.compact();
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

//                clientMap.values().forEach(System.out::println);
            }
        }

        // Fonctionne
        private void decodeM_ALL() {

            if (in.remaining() < Integer.BYTES) {
                return;
            }
            int sizeName = in.getInt();
            if (in.remaining() < sizeName) {
                return;
            }
            ByteBuffer buffName = ByteBuffer.allocate(sizeName);
            for (int i = 0; i < sizeName; i++) {
                buffName.put(in.get());
            }
            buffName.flip();


            ///// Decode message //////
            int sizeMessage;
            if (in.remaining() < Integer.BYTES) {
                return;
            }
            sizeMessage = in.getInt();
            if (in.remaining() < sizeMessage) {
                return;
            }
            ByteBuffer buffMessage = ByteBuffer.allocate(sizeMessage);
            for (int i = 0; i < sizeMessage; i++) {
                buffMessage.put(in.get());
            }
            buffMessage.flip();


            /////// Ecriture dans le buffer out de chacune des personnes connecté et passage en mode WRITE pour ces clefs //////
            ByteBuffer toSend = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + sizeName + Integer.BYTES + sizeMessage);
            toSend.put(PacketType.E_M_ALL.getValue())
                    .putInt(sizeName)
                    .put(buffName)
                    .putInt(sizeMessage)
                    .put(buffMessage);

            clientMap.forEach((sc, clientInfo) -> {
                if (!sc.equals(socketChannel)) {
                    clientInfo.out = toSend.duplicate();
                    clientInfo.status = CurrentStatus.END;
                    clientInfo.selectionKey.interestOps(SelectionKey.OP_WRITE);
                }
            });
            in.compact();
            status = CurrentStatus.END;
        }

        // Pas de readAll en non bloquant
        private void doRead(SelectionKey key) throws IOException {
            System.out.println("In READ");
            if (-1 == socketChannel.read(in)) {
                System.out.println("///////////////////////Closed///////////////////////");
                System.out.println("Client : " + name + " disconnected");
                isClosed = true;
                if (in.position() == 0) {
                    clientMap.remove(socketChannel);
                    socketChannel.close();
                    return;
                }
            }
            buildOutBuffer(key);
            int interest;
            if ((interest = getInterestKey()) != 0) {
                key.interestOps(interest);
            }
        }

        private void doWrite(SelectionKey key) throws IOException {
            System.out.println("In WRITE");
            if (status == CurrentStatus.END) {
                out.flip();
                socketChannel.write(out);

                out.compact();
                if (isClosed) {
                    clientMap.remove(socketChannel);
                    socketChannel.close();
                    return;
                }
                status = CurrentStatus.BEGIN;
            }
            key.interestOps(getInterestKey());
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
            return "ClientInfo{  " +
                    "  \nisClosed=" + isClosed +
                    "  \nstatus=" + status +
                    "  \nname='" + name + '\'' +
                    "  \nin=" + in +
                    "  \nout=" + out +
                    "  \nsocketChannel=" + socketChannel +
                    "  \ncurrentOp=" + currentOp +
                    "  \nadressServer=" + adressServer +
                    "\n}\n";
        }
    }
}

