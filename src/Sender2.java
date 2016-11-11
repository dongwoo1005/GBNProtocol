import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.net.SocketException;

public class sender {
    private final static String WRONG_ARG_NUM = "WARNNING: Not correct number of arguments. Please check README.txt";
    private final static String WARN_NOT_VALID = "WARNNING: Not vaild arguments! Check README.txt for instruction.";
    // used to store the emulator's address and port number. Initilized
    // by taking argument from command-line
    private static String EmulatorAddr;
    private static int sendToPort;
    private static int receivePort;
    private static String fileName;

    // Variables used by readFile to determine how to read file
    private static int Pos = 0;
    private static final int maxToRead = 500;
    private static final int windowSize = 10;

    // A array to hold the packets(in the form of bytes) to send
    private static ArrayList<byte[]> sendList = new ArrayList<byte[]>();

    // Send/receive relative informations
    private static int sendBase = 0;
    private static int nextSeqNum = 0;
    private static DatagramSocket connectionSocket;
    private static PrintWriter seqWriter;
    private static PrintWriter ackWriter;

    public static void main(String argv[]) throws Exception {
        checkArgs(argv);

        // Use the arguements to initialize the program
        initializeState(argv);

        // Read and process file into packets. Also transform packets into bytes.
        processData();

        // Send the data using urt algorithm(GBN in this assignment)
        sendData();

    }

    /*
     * Initialize the states of the sender. Record the arguments.
     */
    public static void initializeState(String argv[]) throws SocketException, FileNotFoundException, UnsupportedEncodingException{
        // store and initialize using arguments
        EmulatorAddr = argv[0];
        sendToPort = Integer.parseInt(argv[1]);
        fileName = argv[3];

        // create a socket to send/receive Datagram
        receivePort = Integer.parseInt(argv[2]);
        connectionSocket = new DatagramSocket(receivePort);


        seqWriter = new PrintWriter("segnum.log", "UTF-8");
        ackWriter = new PrintWriter("ack.log", "UTF-8");
    }

    /*
     * Send the data using rdt algorithm(GBN in this assignment) In this
     * assignment, window size is set to 10.
     */
    public static void sendData() throws Exception {
        // Keep trying to send packet, until all packets are sent and break the loop
        while (sendBase < sendList.size()) {
            // Keep send out packet when there are availible window slot
            while (sendBase + windowSize > nextSeqNum && nextSeqNum < sendList.size()) {
                sendPacket(nextSeqNum);
                nextSeqNum++;
            }
            // When the window is full, start receiving acks.
            receiveAck();
        }
        // Send out EOT
        closeConnection();
    }

    /*
     * A function that close the rdt connection
     */
    public static void closeConnection() throws Exception{
        // holders for send/received data
        byte[] receivedData = new byte[9999];

        Packet eotPacket = Packet.createEOT(nextSeqNum);
        sendList.add(eotPacket.getUDPdata());

        // receive the response
        DatagramPacket receivePacket;

        //Send EOT and listen for reply from another host. If receive other packets, re-transmission
        while (true) {
            // Last one in the sendList is the one that we just added
            sendPacket(sendList.size()-1);

            receivePacket = new DatagramPacket(receivedData, receivedData.length);
            connectionSocket.receive(receivePacket);
            Packet rcvPacket = null;
            rcvPacket = Packet.parseUDPdata(receivePacket.getData());

            if (rcvPacket.getType() == 2) {
                // Receiving an EOT. And then clean up the stuff
                connectionSocket.close();
                seqWriter.close();
                ackWriter.close();
                return;
            }
        }
    }

    /*
     * Send the specific packet to the destination
     */
    public static void sendPacket(int packetNum) throws Exception {
        // create a socket to send/receive Datagram
        DatagramSocket sendSocket = new DatagramSocket();

        // create a datagramPacket
        InetAddress iPAddress = InetAddress.getByName(EmulatorAddr);
        DatagramPacket sendPacket = new DatagramPacket(sendList.get(packetNum), sendList.get(packetNum).length, iPAddress, sendToPort);

        // send the packet
        sendSocket.send(sendPacket);

        //record the sequence number, only record the data packet.
        if (Packet.parseUDPdata(sendList.get(packetNum)).getType() == 1) {
            seqWriter.println(Packet.parseUDPdata(sendList.get(packetNum)).getSeqNum());
        }
    }

    /*
     * Process the send file and make them into packets. Also transform packets into
     * bytes.
     */
    public static void processData() throws IOException {
        File f = new File(fileName);
        long FileLength = f.length();
        int count = 0;
        Packet newPacket = null;

        while (Pos < FileLength) {
            String data = readFile(Pos, maxToRead, f);
            try {
                newPacket = Packet.createPacket(count, data);
                count++;
            } catch (Exception e) { // Data too large for a packet to hold
                System.err.println(e.getMessage());
                System.exit(1);
            }

            sendList.add(newPacket.getUDPdata());
        }
    }

    /*
     * Read a number of characters from a file, return as a string Parameters:
     * lengthToRead: length of a file to read position: position in a file to
     * read
     */
    public static String readFile(int position, int lengthToRead, File f)
            throws IOException {
        // read from a file
        Reader r = new BufferedReader(new InputStreamReader(
                new FileInputStream(f), "US-ASCII"));
        try {
            StringBuilder resultBuilder = new StringBuilder();
            int count = 0;
            int intch;
            r.skip(Pos);
            while (((intch = r.read()) != -1) && count < lengthToRead) {
                resultBuilder.append((char) intch);
                count++;
            }
            Pos += count;
            return resultBuilder.toString();
        } finally {
            // clean up
            r.close();
        }
    }

    /*
     * Check if arguements are valid If not valid, the programs will exit with
     * corresponding error msgs
     */
    public static void checkArgs(String argv[]) {
        // check if the number of arguments is correct(should be 4 in this case)
        if (argv.length != 4) {
            System.err.println(WRONG_ARG_NUM);
            System.exit(1);
        }

        try {
            // Check if the four arguments are in the correct format.
            InetAddress iPAddress = InetAddress.getByName(argv[0]);
            int tmp = (int) Integer.parseInt(argv[1]);
            int tmp1 = (int) Integer.parseInt(argv[2]);
            File f = new File(argv[3]);
            if (!f.exists()) {
                throw new Exception("Invalid file name");
            }

        } catch (Exception e) {
            System.out.println(WARN_NOT_VALID);
            System.exit(1);
        }
    }

    /*
     * Try to listen to the socket and wait for acknowledgements. Also use future object and ExecutorService
     * to time the base packet. Throw exception if timeout
     */
    public static void receiveAck() throws Exception {

        // Use the executorService to create a new thread.
        // The created new thread will be listening for Acks.
        // The main thread will do the timing
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(new Task());

        try {
            //Set the timeout for the task. If timeout, re-transmission.
            future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // Set the variable to re-send all the packets starting from sendBase
            nextSeqNum = sendBase;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        executor.shutdown();
    }

    /*
     * Used by a executor service to listen for the Acks.
     */
    public static void receiveTask() throws Exception{
        // holders for send/received data
        byte[] receivedData = new byte[9999];

        // receive the response
        DatagramPacket receivePacket;
        while (true) {
            receivePacket = new DatagramPacket(receivedData, receivedData.length);

            connectionSocket.receive(receivePacket);

            Packet rcvPacket = null;

            rcvPacket = Packet.parseUDPdata(receivePacket.getData());
            if (rcvPacket.getType() == 0) {
                // Receiving an ack. Need to check if it's the expecting ack
                ackWriter.println(rcvPacket.getSeqNum());
                if (checkSeqNum(sendBase, rcvPacket.getSeqNum())) { // check if the ack number is the one expected
                    break; // break the receive loop to send new packet since
                    // there's available slot
                }
            } else {
                System.err.println("Should not receive data/EOT packet while waiting for Acks!");
                System.exit(0);
            }
        }
    }

    /*
     * A function to check if the received ack number is in the expected range
     */
    public static boolean checkSeqNum(int sb, int seqNum){
        // Look forward for 10 numbers. If seqNum is in this range, then it's valid ack number
        for (int i = 0; i < 10; i++) {
            if (((sb+i)%32) == seqNum) {
                //update the sendBase here, since the Ack received is always the highest one
                sendBase = sendBase + i + 1;
                return true;
            }
        }
        return false;
    }
}



/*
 * A simple class that simply make a thread sleep.
 * The sleep time of it should always larger than the timeout value so that timeout can be triggered
 */
class Task implements Callable<String> {
    @Override
    public String call() throws Exception {
        sender.receiveTask();
        return "NotGonnaWakeUp!";
    }
}