import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
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
    static final int MAXLEN = 500;
    static final int PACKET_SIZE = 512;
    static final int TIMEOUT_DELAY = 200;
    static final String SEQ_LOG_FILE_NAME = "seqnum.log";
    static final String ACK_LOG_FILE_NAME = "ack.log";

    static String nEmulatorHostAddr;
    static int nEmulatorPortGetData, senderPortGetAck;
    static String fileName;
    static ArrayList<Packet> packets;

    static int nextSeqNum = 1;
    static int base = 1;
    static Timer timer = null;

    static BufferedReader bufferedReader = null;
    static BufferedWriter seqLogWriter = null;
    static BufferedWriter ackLogWriter = null;


    static class ReceiveAckTask implements Runnable {

        private Thread receiverThread;

        public ReceiveAckTask() {
            this.receiverThread = new Thread(this);
        }

        @Override
        public void run() {

            DatagramSocket getAckUdpSocket = null;
            try {
                // Create a UDP socket on sender port to get Acks
                getAckUdpSocket = new DatagramSocket(senderPortGetAck);
                while (true) {

                    // ReliableDataTransfer Receive Packet - extract
                    byte[] receiveData = new byte[PACKET_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    getAckUdpSocket.receive(receivePacket);            // wait until received
                    Packet myPacket = Packet.parseUDPdata(receiveData);

                    if (myPacket.getType() == 2) break;

                    ackLogWriter.write(myPacket.getSeqNum() + "\n");    // write log

                    base = myPacket.getSeqNum() + 1;
                    if (base == nextSeqNum) stopTimer();
                    else startTimer();
                }

            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
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


    static class SendDataTask {

        public void run() {
            try {
                for ( ;; ) {

                    char[] charArr = new char[MAXLEN];
                    int result = bufferedReader.read(charArr, 0, MAXLEN);
                    if (result < 0) break;

                    String data = String.valueOf(charArr);
                    int emptyIndex = data.indexOf("\u0000");
                    if (emptyIndex > 0) data = data.substring(0, emptyIndex);

                    reliableDataTransferSend(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    private static void reliableDataTransferSend(String data) throws Exception {
        if (nextSeqNum < base + N) {
            sendData(data);
        } else {
            refuseData(data);   // hold and wait until base++
        }
    }


    private static void sendData(String data) throws Exception {
        Packet packet = Packet.createPacket(nextSeqNum, data);
        packets.add(packet);
        unreliableDataTransferSend(packet, nEmulatorHostAddr, nEmulatorPortGetData);
        if (base == nextSeqNum) startTimer();
        nextSeqNum += 1;
    }


    private static void refuseData(String data) throws Exception {
        while(nextSeqNum >= base + N) {}
        sendData(data);
    }


    private static void unreliableDataTransferSend(Packet packet, String nEmulatorHostAddr, int nEmulatorPortGetData)
            throws IOException {

        // Create a UDP socket
        DatagramSocket udpSocket = new DatagramSocket();

        // Send packet with message
        byte[] sendData = packet.getUDPdata();
        InetAddress ipAddress = InetAddress.getByName(nEmulatorHostAddr);
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, nEmulatorPortGetData);
        udpSocket.send(sendPacket);

        seqLogWriter.write(packet.getSeqNum());

        udpSocket.close();
    }


    private static void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.schedule(timeout, TIMEOUT_DELAY);
    }


    private static void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }


    private static TimerTask timeout = new TimerTask() {
        @Override
        public void run() {
            startTimer();
            for (int i=0; i<nextSeqNum; i+=1) {
                try {
                    unreliableDataTransferSend(packets.get(base), nEmulatorHostAddr, nEmulatorPortGetData);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };


    private static void closeAll() throws IOException {
        if (ackLogWriter != null) ackLogWriter.close();
        if (seqLogWriter != null) seqLogWriter.close();
        if (bufferedReader != null) bufferedReader.close();
        stopTimer();
    }


    public static void main(String[] args) throws Exception {

        // Read command line arguments
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

        nEmulatorHostAddr = args[0];
        nEmulatorPortGetData = Integer.valueOf(args[1]);
        senderPortGetAck = Integer.valueOf(args[2]);
        fileName = args[3];

        packets = new ArrayList<>();

        bufferedReader = Files.newBufferedReader(Paths.get(fileName));

        // seq log writer
        File seqLogFile = new File(SEQ_LOG_FILE_NAME);
        if (seqLogFile.exists()) {
            seqLogFile.delete();
            seqLogFile.createNewFile();
        }
        seqLogWriter = Files.newBufferedWriter(Paths.get(SEQ_LOG_FILE_NAME), (OpenOption) null);

        // ack log writer
        File ackLogFile = new File(ACK_LOG_FILE_NAME);
        if (ackLogFile.exists()) {
            ackLogFile.delete();
            ackLogFile.createNewFile();
        }
        ackLogWriter = Files.newBufferedWriter(Paths.get(ACK_LOG_FILE_NAME), (OpenOption) null);

        // Begin receiving ACKs task on a separate thread
        ReceiveAckTask receiver = new ReceiveAckTask();
        receiver.start();

        // Begin sending data on the main thread
        SendDataTask sender = new SendDataTask();
        sender.run();
    }
}
