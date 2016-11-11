import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * GBN
 * Created by dwson Son (20420487)
 * on 11/10/16
 * d3son@uwaterloo.ca
 */
public class Receiver {

    static final String LOG_FILE_NAME = "arrival.log";
    static final int PACKET_SIZE = 512;
    static final int MAX_SEQ = 32;

    static String nEmulatorHostname;
    static int nEmulatorPortGetAcks, receiverPortGetData;
    static String outputFileName;

    static int expectedSeqNum;
    static Packet currentSendPacket = null;

    static BufferedWriter outputWriter = null;
    static BufferedWriter logWriter = null;

    static class DataReceiver {

        public void run() throws IOException {

            try {
                while(true) {

                    // Create a UDP socket on receiver port to get data
                    DatagramSocket getDataUdpSocket = new DatagramSocket(receiverPortGetData);

                    // ReliableDataTransfer Receive Packet - extract
                    byte[] receiveData = new byte[PACKET_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    getDataUdpSocket.receive(receivePacket);            // wait until received
                    Packet myPacket = Packet.parseUDPdata(receiveData);

                    // Log
                    System.out.println("received: " + myPacket.getSeqNum());
                    logWriter.write(myPacket.getSeqNum() + "\n");

                    // Create a UDP socket
                    DatagramSocket sendAckUdpSocket = new DatagramSocket();

                    // Check the sequence number of the packet and update currentSendPacket
                    if (myPacket.getSeqNum() == expectedSeqNum) {
                        System.out.println("Match");
                        if (myPacket.getType() != 2) outputWriter.write(new String(myPacket.getData()));
                        currentSendPacket = myPacket.getType() == 2 ?
                                Packet.createEOT(expectedSeqNum) : Packet.createACK(expectedSeqNum);
                        expectedSeqNum += 1;
                        expectedSeqNum %= MAX_SEQ;
                    }

                    // send Ack packet
                    byte[] sendData = currentSendPacket.getUDPdata();
                    InetAddress ipAddress = InetAddress.getByName(nEmulatorHostname);
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, nEmulatorPortGetAcks);
                    sendAckUdpSocket.send(sendPacket);
                    System.out.println("sent: " + currentSendPacket.getSeqNum());

                    sendAckUdpSocket.close();

                    // exit if EOT
                    if (myPacket.getType() == 2) {
                        System.out.println("EOT received");
                        if (getDataUdpSocket != null) getDataUdpSocket.close();
                        break;
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeAll();
            }
        }
    }

    private static void closeAll() throws IOException {
        if (outputWriter != null) outputWriter.close();
        if (logWriter != null) logWriter.close();
    }

    public static void main(String[] args) throws Exception {

        // Error and exit appropriately
        if (args.length != 4) {
            System.err.println(
                    "Required command line parameters:"
                            + " <hostname for the nEmulator>"
                            + " <UDP port number used by the emulator to receive ACKs from the Receiver>"
                            + " <UDP port number used by the Receiver to receive data from the emulator>"
                            + " <name of the file into which the received data is written>"
            );
            System.exit(1);
        }

        // Read command line arguments
        nEmulatorHostname = args[0];
        nEmulatorPortGetAcks = Integer.valueOf(args[1]);
        receiverPortGetData = Integer.valueOf(args[2]);
        outputFileName = args[3];

        // default variables
        expectedSeqNum = 1;
        currentSendPacket = Packet.createACK(expectedSeqNum);

        // Output
        File outputFile = new File(outputFileName);
        if (!outputFile.exists()) outputFile.createNewFile();
        outputWriter = Files.newBufferedWriter(Paths.get(outputFileName), StandardOpenOption.WRITE);

        // Log
        File logFile = new File(LOG_FILE_NAME);
        logFile.delete();
        logFile.createNewFile();
        logWriter = Files.newBufferedWriter(Paths.get(LOG_FILE_NAME), StandardOpenOption.WRITE);

        // Receiver
        DataReceiver dataReceiver = new DataReceiver();
        dataReceiver.run();
    }
}
