import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * GBN
 * Created by dwson Son (20420487)
 * on 11/10/16
 * d3son@uwaterloo.ca
 */
public class Sender {

    static final int N = 10;
    static final int MAXLEN = 50;
    static final int PACKET_SIZE = 512;
    static final int TIMEOUT_DELAY = 50;
    static final String SEQ_LOG_FILE_NAME = "seqnum.log";
    static final String ACK_LOG_FILE_NAME = "ack.log";

    static String nEmulatorHostAddr;
    static int nEmulatorPortGetData, senderPortGetAck;
    static String fileName;
    static ArrayList<Packet> packets;

    static int nextSeqNum = 1;
    static int seqNumCount;
    static int base = 1;
    static Timer timer = null;

    static BufferedReader bufferedReader = null;
    static BufferedWriter seqLogWriter = null;
    static BufferedWriter ackLogWriter = null;


    private static void checkArguments(String[] args) {
        // check number of arguments
        if (args.length != 4) {
            System.err.println(
                    "Required command line parameters:"
                            + " <host address of the nEmulator>"
                            + " <UDP port number used by the emulator to receive data from the sender>"
                            + " <UDP port number used by the sender to receive ACKs from the emulator>"
                            + " <name of the file to be transferred>"
            );
            System.exit(1);
        }
    }


    private static void initialize(String[] args) throws IOException {

        // read from commandline arguments
        nEmulatorHostAddr = args[0];
        nEmulatorPortGetData = Integer.valueOf(args[1]);
        senderPortGetAck = Integer.valueOf(args[2]);
        fileName = args[3];

        packets = new ArrayList<>();

        bufferedReader = Files.newBufferedReader(Paths.get(fileName));

        // seq log writer
        File seqLogFile = new File(SEQ_LOG_FILE_NAME);
        seqLogFile.delete();
        seqLogFile.createNewFile();
        seqLogWriter = Files.newBufferedWriter(Paths.get(SEQ_LOG_FILE_NAME), StandardOpenOption.WRITE);

        // ack log writer
        File ackLogFile = new File(ACK_LOG_FILE_NAME);
        ackLogFile.delete();
        ackLogFile.createNewFile();
        ackLogWriter = Files.newBufferedWriter(Paths.get(ACK_LOG_FILE_NAME), StandardOpenOption.WRITE);
    }


    private static void processData() throws Exception {

        seqNumCount = 0;
        for ( ;; ) {

            char[] charArr = new char[MAXLEN];
            int result = bufferedReader.read(charArr, 0, MAXLEN);
            if (result < 0) break;

            String data = String.valueOf(charArr);
            int emptyIndex = data.indexOf("\u0000");
            if (emptyIndex > 0) data = data.substring(0, emptyIndex);

            Packet packet = Packet.createPacket(seqNumCount + 1, data);
            packets.add(packet);
            seqNumCount += 1;
        }
    }


    private static void reliableDataTransferSend(Packet packet) throws Exception {
        System.out.println("rdt_send");
        if (nextSeqNum < base + N) {
            sendData(packet);
        } else {
            refuseData(packet);   // hold and wait until base++
        }
        System.out.println("finish rdt_send");
        return;
    }


    private static void sendData(Packet packet) throws Exception {
        System.out.println("sendData");
        unreliableDataTransferSend(packet, nEmulatorHostAddr, nEmulatorPortGetData);
        if (base == nextSeqNum) {
            System.out.print("base == nextSeqNum");
            startTimer();
        }
        nextSeqNum += 1;
    }


    private static void refuseData(Packet packet) throws Exception {
        System.out.println("refuseData");
        while(nextSeqNum >= base + N) {
//            System.out.println("wait");
        }
        sendData(packet);
    }


    private static void unreliableDataTransferSend(Packet packet, String nEmulatorHostAddr, int nEmulatorPortGetData) {

        System.out.println(packet.getSeqNum() + "udt_send");
        // Create a UDP socket
        DatagramSocket udpSocket = null;
        try {
            udpSocket = new DatagramSocket();

            // Send packet with message
            byte[] sendData = packet.getUDPdata();
            InetAddress ipAddress = InetAddress.getByName(nEmulatorHostAddr);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, nEmulatorPortGetData);
            udpSocket.send(sendPacket);

            seqLogWriter.write(packet.getSeqNum());
            System.out.println(packet.getSeqNum() + "write seq log");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (udpSocket != null) udpSocket.close();
            System.out.println(packet.getSeqNum() + "finish udt_send");
        }
    }


    private static void startTimer() {
        System.out.println("startTimer");
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimeoutTask(), TIMEOUT_DELAY);
    }


    private static void stopTimer() {
        System.out.println("in stopTimer");
        if (timer != null) {
            System.out.println("stopped");
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }


    private static class TimeoutTask extends TimerTask {

        Thread timeoutTask;

        public TimeoutTask() {
            this.timeoutTask = new Thread(this);
        }

        @Override
        public void run() {
            System.out.println("timertask");
            startTimer();
            for (int i=base; i<nextSeqNum; i+=1) {
                unreliableDataTransferSend(packets.get(i-1), nEmulatorHostAddr, nEmulatorPortGetData);
            }
        }
    };


    private static void closeAll() throws IOException {
        System.out.println("closeAll");
        if (ackLogWriter != null) ackLogWriter.close();
        if (seqLogWriter != null) seqLogWriter.close();
        if (bufferedReader != null) bufferedReader.close();
        stopTimer();
    }


    static class ReceiveAckTask implements Runnable {

        private Thread receiverThread;

        public ReceiveAckTask() {
            this.receiverThread = new Thread(this);
        }

        @Override
        public void run() {

            DatagramSocket getAckUdpSocket = null;
            try {
                while (true) {

                    // Create a UDP socket on sender port to get Acks
                    getAckUdpSocket = new DatagramSocket(senderPortGetAck);

                    // ReliableDataTransfer Receive Packet - extract
                    byte[] receiveData = new byte[PACKET_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    getAckUdpSocket.receive(receivePacket);            // wait until received
                    Packet myPacket = Packet.parseUDPdata(receiveData);

                    if (myPacket.getType() == 2) break;

                    ackLogWriter.write(myPacket.getSeqNum() + "\n");    // write log

                    base = myPacket.getSeqNum() + 1;
                    if (base == nextSeqNum) stopTimer();
                    else {
                        System.out.println("receive run else");
                        startTimer();
                    }
                    getAckUdpSocket.close();
                }

            } catch (SocketException e) {
                System.out.println("Receive Task Socket Exception");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Receive Task IO Exception");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Receive Task Exception Exception");
                e.printStackTrace();
            } finally {
                if (getAckUdpSocket != null) getAckUdpSocket.close();
                try {
                    closeAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void start() {
            receiverThread.start();
        }
    }


    public static void main(String[] args) throws Exception {

        // Read command line arguments
        checkArguments(args);
        initialize(args);
        processData();

        // Begin receiving ACKs task on a separate thread
        ReceiveAckTask receiver = new ReceiveAckTask();
        receiver.start();

        // Begin sending data on the main thread
        for (int i=0; i<seqNumCount; i+=1) {
            System.out.println("i=" + i);
            reliableDataTransferSend(packets.get(i));
            System.out.println("i=" + i);
        }
        System.out.println("done");
    }
}
