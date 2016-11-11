import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.jar.Pack200.Packer;
import java.net.SocketException;

public class Receiver2 {
    private final static String WRONG_ARG_NUM = "WARNNING: Not correct number of arguments. Please check README.txt";
    private final static String WARN_NOT_VALID = "WARNNING: Not vaild arguments! Check README.txt for instruction.";

    // used to store the emulator's address and port number. Initilized
    // by taking argument from command-line
    private static String EmulatorAddr;
    private static int sendToPort;
    private static int receivePort;
    private static String outputFile;

    // DatagramSocket and relative information for send/receive
    private static DatagramSocket connectionSocket;
    private static int highestAck = 0;
    private static int expectedSeqNum = 0;
    private static boolean hasRev = false;
    private static PrintWriter seqWriter;
    private static PrintWriter fileWriter;


    public static void main(String argv[]) throws Exception{
        checkArgs(argv);

        // Use the arguements to initialize the program
        initializeState(argv);

        receiveData();
    }

    /*
 * Initialize the states of the receiver. Record the arguments.
 */
    public static void initializeState(String argv[]) throws SocketException, FileNotFoundException, UnsupportedEncodingException{
        // store and initialize using arguments
        EmulatorAddr = argv[0];
        sendToPort = Integer.parseInt(argv[1]);
        outputFile = argv[3];

        // create a socket to send/receive Datagram
        receivePort = Integer.parseInt(argv[2]);
        connectionSocket = new DatagramSocket(receivePort);


        // Create writer object to write seq num to file
        seqWriter = new PrintWriter("arrival.log", "UTF-8");
        fileWriter = new PrintWriter(outputFile, "UTF-8");
    }

    /*
     * Receive data from the socket and then send back acknowledgements.
     */
    public static void receiveData() throws Exception{
        //holders for send/received data
        byte[]receivedData = new byte[9999];

        //receive the response
        DatagramPacket receivePacket;
        while (true) {
            receivePacket = new DatagramPacket(receivedData, receivedData.length);
            connectionSocket.receive(receivePacket);
            //Read the data and check type
            Packet rcvPacket = null;
            rcvPacket = Packet.parseUDPdata(receivePacket.getData());
            if (rcvPacket.getType() == 1) {
                processData(rcvPacket);
            } else if (rcvPacket.getType() == 2) {
                closeConnection();
                break;
            }
            else{
                System.err.println("Should not receive acknowledgement!");
                System.exit(0);
            }

        }
    }

    /*
     * A function to close the connection and exit
     */
    public static void closeConnection() throws Exception{
        Packet eotPacket = Packet.createEOT(expectedSeqNum);
        sendPacket(eotPacket);

        connectionSocket.close();
        seqWriter.close();
        return;
    }

    /*
     * Check the argument packet. Send the acknowledgement according to if the sequence
     * number of the packet is expected
     */
    public static void processData(Packet rcvPacket) throws IOException, Exception {
        seqWriter.println(rcvPacket.getSeqNum());
        if (rcvPacket.getSeqNum() == expectedSeqNum) {
            //The seqNum is the one expected.
            sendPacket(Packet.createACK(expectedSeqNum));
            highestAck = expectedSeqNum;
            expectedSeqNum = (expectedSeqNum+1)%32;
            hasRev = true;

            // Write the data to output file
            fileWriter.print(new String(rcvPacket.getData()));
        } else if(rcvPacket.getSeqNum() != expectedSeqNum) {
            // Re-send the highest acknowledgement because of receiving unexpected packet
            if (hasRev) {
                //If the first packet is lost, we should not send back ack.
                sendPacket(Packet.createACK(highestAck));
            }
        }
    }

    /*
     * Transfer the packet into byte array and send it to the network to destination
     */
    public static void sendPacket(Packet pkt) throws IOException{
        // create a socket to send/receive Datagram
        DatagramSocket sendSocket = new DatagramSocket();

        // create a datagramPacket
        InetAddress iPAddress = InetAddress.getByName(EmulatorAddr);
        DatagramPacket sendPacket = new DatagramPacket(pkt.getUDPdata(),
                pkt.getUDPdata().length, iPAddress, sendToPort);

        // send the packet
        sendSocket.send(sendPacket);
    }

    /* Check if arguements are valid
     * If not valid, the programs will exit with corresponding error msgs */
    public static void checkArgs(String argv[]){
        //check if the number of arguments is correct(should be 4 in this case)
        if (argv.length != 4) {
            System.err.println(WRONG_ARG_NUM);
            System.exit(1);
        }

        try {
            //Check if the four arguments are in the correct format.
            InetAddress iPAddress = InetAddress.getByName(argv[0]);
            int tmp = (int)Integer.parseInt(argv[1]);
            int tmp1 = (int)Integer.parseInt(argv[2]);
            File f = new File(argv[3]);
            if(!f.exists()) {
                throw new Exception("Invalid file name");
            }

        } catch (Exception e) {
            System.out.println(WARN_NOT_VALID);
            System.exit(1);
        }
    }
}
