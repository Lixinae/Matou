package matou.server;

import java.io.IOException;
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

    /* Codes pour la reception de paquets */
    private final static byte E_PSEUDO = 1;
    private final static byte DC_PSEUDO = 6;
    private final static byte D_LIST_CLIENT_CO = 7;
    private final static byte E_M_ALL = 9;

    /* Code pour l'envoie de paquets */
    private final static byte R_LIST_CLIENT_CO = 8;

    private final static byte R_PSEUDO = 10;


//    private final static int TIME_OUT = 1000;

    private final static Charset UTF8_charset = Charset.forName("UTF8");
    private final HashMap<SocketChannel, String> clientMap = new HashMap<>();

    SelectionKey key;

    public RequestProcessor(SelectionKey key) {
        this.key = key;
    }

    void processRequest() {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();

        boolean dc = false;
        byteBuffer.flip();
        if (byteBuffer.remaining() < Byte.BYTES) {
            return;
        }
        byte b = byteBuffer.get();
        System.out.println("byte = " + b);
        switch (b) {
            case E_PSEUDO:
                System.out.println("Entering decode pseudo");
                String pseudo = decodeE_PSEUDO(byteBuffer);
                if (null == pseudo) {
                    System.err.println("Could not read name");
                }
//                if (pseudoAlreadyExists(pseudo)) {
//                    sendAnswerPseudoExists(true, socketChannel);
//                } else {
//                    sendAnswerPseudoExists(false, socketChannel);
//                    clientMap.put(socketChannel, pseudo);
//                }
                clientMap.put(socketChannel, pseudo);
//                System.out.println("Liste des clients connecté " + clientMap.values());
                System.out.println("Exiting decode pseudo");
                break;
            case DC_PSEUDO:
                System.out.println("Entering disconnection");
                System.out.println("Client " + clientMap.get(socketChannel) + " disconnected");
                clientMap.remove(socketChannel);
                try {
                    socketChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                dc = true;
                key.cancel();
                System.out.println("Exiting disconnection");
                break;
            case D_LIST_CLIENT_CO:
                // TODO Test ça
                System.out.println("Entering demand list client");
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
                System.out.println("Exiting demand list client");
                break;
            case E_M_ALL:
                // Send to each socketChannel connected
                // Si socket en mode read -> attendre fin de la lecture et envoyer
                System.out.println("Entering envoie message all");
                writeM_ALL(byteBuffer, socketChannel);
                System.out.println("Exiting envoie message all");
                break;
            default:
                System.err.println("Error : Unkown code " + b);
        }
        // TODO faire attention a recursivite -> ne devrait pas y avoir de soucis mais on ne sais jamais
        // // Si le client spam le serveur
        // -> analyser ce qui reste dans le bytebuffer
        if (byteBuffer.hasRemaining()) {

            System.err.println("This should only happen if the server is spammed by the client");
            processRequest();
        }
        if (!dc) {
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void writeM_ALL(ByteBuffer byteBuffer, SocketChannel socketChannel) {
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
        // TODO
        // Peut etre avoir besoin de savoir si la channel est en read ou write
        ByteBuffer bbOut = byteBuffer.duplicate();
        clientMap.forEach((socketChannel, pseudo) -> {
            if (!clientSocketChannel.equals(socketChannel)) {
                try {
//                    System.out.println("DEBUG : bbOut = " + bbOut);
                    while (bbOut.hasRemaining()) {
                        socketChannel.write(bbOut);
                    }
//                    System.out.println("DEBUG : bbOut après write = " + bbOut);

                } catch (IOException e) {
                    System.err.println("Could not write on channel");
                    e.printStackTrace();
                }
                bbOut.rewind(); // Remet la position au début pour une réutilisation
            }
        });
    }

    private String decodeE_PSEUDO(ByteBuffer byteBuffer) {
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


}
