package matou.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;

/**
 * Project :Matou
 * Created by Narex on 09/04/2016.
 */
class RequestProcessor {
    /*                                    */
    /* Codes pour la reception de paquets */
    /*                                    */

    /* Recuperation du pseudo que le client envoie */
    private final static byte E_PSEUDO = 1;
    /* Demande d'une connection d'un client à un autre */
    private final static byte CO_CLIENT_TO_CLIENT = 2;
    /* Demande de deconnection d'un client */
    private final static byte DC_PSEUDO = 6;
    /* Demande la liste des clients */
    private final static byte D_LIST_CLIENT_CO = 7;
    /* Demande d'envoie à tous */
    private final static byte E_M_ALL = 9;
    /* Reception de l'adresse server du client */
    private static final byte E_ADDR_SERV_CLIENT = 11;

    /*                               */
    /* Code pour l'envoie de paquets */
    /*                               */

    /* Reponse a la demande de la liste des clients*/
    private final static byte R_LIST_CLIENT_CO = 8;

    /* Reponse du serveur au client sur la disponibilite de son pseudo*/
    private final static byte R_PSEUDO = 10;

    private final static Charset UTF8_charset = Charset.forName("UTF8");

    // Liste des clients connecte avec leurs adresses respectives sur lesquels le serveur peut répondre.
    private final HashMap<String, SocketChannel> clientMap = new HashMap<>();

    // Permet de stocker les serverSocketChannel de chacun des clients
    private final HashMap<String, InetSocketAddress> clientMapServer = new HashMap<>();


    public RequestProcessor() {

    }

    public void processRequest(SelectionKey key) {

        cleanMapFromInvalidElements();

        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();

        boolean dc = false;
        byteBuffer.flip();
        if (byteBuffer.remaining() < Byte.BYTES) {
            return;
        }
        byte b = byteBuffer.get();
        switch (b) {
            case E_PSEUDO:
                System.out.println("Entering decode pseudo");
                decodeE_PSEUDO(socketChannel, byteBuffer);
                System.out.println("Exiting decode pseudo");
                break;
            case CO_CLIENT_TO_CLIENT:
                System.out.println("Entering co client");
                decodeCO_CLIENT_TO_CLIENT(byteBuffer);
                System.out.println("Exiting co client");
                break;
            case DC_PSEUDO:
                System.out.println("Entering disconnection");
                dc = decodeDC_PSEUDO(key, socketChannel);
                System.out.println("Exiting disconnection");
                break;
            case D_LIST_CLIENT_CO:
                System.out.println("Entering demand list client");
                if (decodeD_LIST_CLIENT_CO(socketChannel)) {
                    // Enorme erreur si on arrive ici
                    return;
                }
                System.out.println("Exiting demand list client");
                break;
            case E_M_ALL:
                System.out.println("Entering envoie message all");
                decodeM_ALL(byteBuffer, socketChannel);
                System.out.println("Exiting envoie message all");
                break;
            case E_ADDR_SERV_CLIENT:
                System.out.println("Entering envoie adresse serveur client");
                decodeE_ADDR_SERV_CLIENT(byteBuffer, socketChannel);
                System.out.println("Exiting envoie adresse serveur client");
                break;

            default:
                System.err.println("Error : Unkown code " + b);
                break;
        }

        // Si le client spam le serveur
        // -> analyser ce qui reste dans le bytebuffer
        if (byteBuffer.hasRemaining()) {
            System.err.println("This should only happen if the server is spammed by a client");
            processRequest(key);
        }
        if (!dc) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void decodeE_PSEUDO(SocketChannel socketChannel, ByteBuffer byteBuffer) {
        String pseudo = decodeE_PSEUDOannexe(byteBuffer);
        if (null == pseudo) {
            System.err.println("Error : Could not read name");
        }
        if (pseudoAlreadyExists(pseudo)) {
            sendAnswerPseudoExists(true, socketChannel);
        } else {
            sendAnswerPseudoExists(false, socketChannel);
            clientMap.put(pseudo, socketChannel);
        }
    }

    private String decodeE_PSEUDOannexe(ByteBuffer byteBuffer) {
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
        tempo.put(byteBuffer);
        tempo.flip();
        return UTF8_charset.decode(tempo).toString();
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
        }
    }


    // TODO A garder
    private boolean pseudoAlreadyExists(String pseudo) {
        return clientMap.containsKey(pseudo);
    }

    private void decodeCO_CLIENT_TO_CLIENT(ByteBuffer byteBuffer) {
        int sizeConnectTo = byteBuffer.getInt();

        ByteBuffer destinataire = ByteBuffer.allocate(sizeConnectTo);
        for (int i = 0; i < sizeConnectTo; i++) {
            destinataire.put(byteBuffer.get());
        }
        destinataire.flip();
        String dest = UTF8_charset.decode(destinataire).toString();
        int sizeSrc = byteBuffer.getInt();
        ByteBuffer src = ByteBuffer.allocate(sizeSrc);
        for (int i = 0; i < sizeSrc; i++) {
            src.put(byteBuffer.get());
        }
        src.flip();
        ByteBuffer buffOut = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + sizeSrc);
        buffOut.put(CO_CLIENT_TO_CLIENT)
                .putInt(sizeSrc)
                .put(src);
        buffOut.flip();
        try {
            clientMap.get(dest).write(buffOut);
        } catch (IOException e) {
            System.err.println("Error : Client closed connection before finished sending");
            e.printStackTrace();
        }
    }

    // TODO A garder
    private boolean decodeDC_PSEUDO(SelectionKey key, SocketChannel socketChannel) {
        String pseudo = findPseudoWithAdress(clientMap, socketChannel);
        System.out.println("Disconnecting client : " + pseudo);
        try {
            clientMap.get(pseudo).close();
            clientMap.remove(pseudo);
            clientMapServer.remove(pseudo);
        } catch (IOException e) {
            e.printStackTrace();
        }
        key.cancel();
        return true;
    }

    // TODO A garder
    private String findPseudoWithAdress(HashMap<String, SocketChannel> input, SocketChannel socketChannel) {
        final String[] pseudo = new String[1];
        input.forEach((p, sc) -> {
            if (sc.equals(socketChannel)) {
                pseudo[0] = p;
            }
        });
        return pseudo[0];
    }

    private void cleanMapFromInvalidElements() {

        clientMap.values().removeIf(e -> remoteAddressToString(e).equals("???"));
        clientMapServer.keySet().removeIf(s -> !clientMap.containsKey(s));

        if (clientMapServer.size() > 0 && clientMap.size() > 0) {
            if (clientMapServer.size() != clientMap.size()) {
                System.err.println("Map de taille différentes");
            }
        }
    }

    private boolean decodeD_LIST_CLIENT_CO(SocketChannel socketChannel) {
        ByteBuffer bbOut = encodeE_LIST_CLIENT_CO();
        if (null == bbOut) {
            System.err.println("This should never happen !!!!! Error : nobody is connected ");
            return true;
        }
        bbOut.flip();
        try {
            while (bbOut.hasRemaining()) {
                socketChannel.write(bbOut);
            }
        } catch (IOException e) {
            System.err.println("Client closed connection before finishing sending");
        }
        return false;
    }

    private void decodeM_ALL(ByteBuffer byteBuffer, SocketChannel socketChannel) {
        int sizeName = byteBuffer.getInt();
        ByteBuffer name = ByteBuffer.allocate(sizeName);
        for (int i = 0; i < sizeName; i++) {
            name.put(byteBuffer.get());
        }
        name.flip();

        int sizeMessage = byteBuffer.getInt();
        ByteBuffer message = ByteBuffer.allocate(sizeMessage);
        for (int i = 0; i < sizeMessage; i++) {
            message.put(byteBuffer.get());
        }
        message.flip();

        ByteBuffer toSend = ByteBuffer.allocate(Byte.BYTES + Integer.BYTES + sizeName + Integer.BYTES + sizeMessage);
        toSend.put(E_M_ALL)
                .putInt(sizeName)
                .put(name)
                .putInt(sizeMessage)
                .put(message);
        toSend.flip();
        writeOneMessageToAll(toSend, socketChannel);
    }

    private void writeOneMessageToAll(ByteBuffer byteBuffer, SocketChannel clientSocketChannel) {
        ByteBuffer bbOut = byteBuffer.duplicate();
        clientMap.forEach((pseudo, socketChannel) -> {
            if (!clientSocketChannel.equals(socketChannel)) {
                try {
                    while (bbOut.hasRemaining()) {
                        socketChannel.write(bbOut);
                    }
                } catch (IOException e) {
                    System.err.println("Error : Could not write on channel of client " + pseudo);
                }
                bbOut.rewind(); // Remet la position au début pour une réutilisation
            }
        });
    }

    private ByteBuffer encodeE_LIST_CLIENT_CO() {
        Long size = calculSizeBufferList();
        if (size <= 0) {
            System.err.println("This should never happen !!!!! Error : nobody is connected ");
            return null;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(size.intValue());
        byteBuffer.put(R_LIST_CLIENT_CO).putInt(clientMapServer.size());
        StringBuilder sb = new StringBuilder();
        clientMapServer.forEach((pseudo, inetSocketAddress) -> {
            sb.delete(0, sb.length());
            sb.append(inetSocketAddress.getHostString())
                    .append(":")
                    .append(inetSocketAddress.getPort());

            String to_encode = sb.toString();
            byteBuffer.putInt(pseudo.length())
                    .put(UTF8_charset.encode(pseudo))
                    .putInt(sb.length())
                    .put(UTF8_charset.encode(to_encode));
                }
        );
        return byteBuffer;
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString().replace("/", "");
        } catch (IOException e) {
            return "???";
        }
    }

    private long calculSizeBufferList() {
        final Long[] total = {0L};
        total[0] += Byte.BYTES;
        total[0] += Integer.BYTES;
        clientMapServer.forEach((key, value) -> {
            // Peut se simplifier mais cette forme est plus clair
            long clientSize = Integer.BYTES + key.length() + Integer.BYTES + value.toString().length();
            total[0] += clientSize;
        });
        return total[0];
    }

    private void decodeE_ADDR_SERV_CLIENT(ByteBuffer byteBuffer, SocketChannel socketChannel) {
        int sizeTotal = byteBuffer.getInt();
        ByteBuffer tempo = ByteBuffer.allocate(sizeTotal);
        for (int i = 0; i < sizeTotal; i++) {
            tempo.put(byteBuffer.get());
        }
        tempo.flip();
        String fullChain = UTF8_charset.decode(tempo).toString();

        int splitIndex = fullChain.lastIndexOf(':');
        String host = fullChain.substring(0, splitIndex);
        int port = Integer.parseInt(fullChain.substring(splitIndex + 1));
        String clientName = findPseudoWithAdress(clientMap, socketChannel);
        clientMapServer.put(clientName, new InetSocketAddress(host, port));
    }
}
