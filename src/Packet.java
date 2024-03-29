/**
 * GBN
 * Created by dwson Son (20420487)
 * on 11/10/16
 * d3son@uwaterloo.ca
 */

// common packet class used by both SENDER and RECEIVER

import java.nio.ByteBuffer;

public class Packet {

    // constants
    private final int maxDataLength = 500;
    private final int SeqNumModulo = 32;

    // data members
    private int type;
    private int seqnum;
    private String data;

    //////////////////////// CONSTRUCTORS //////////////////////////////////////////

    // hidden constructor to prevent creation of invalid packets
    private Packet(int Type, int SeqNum, String strData) throws Exception {
        // if data seqment larger than allowed, then throw exception
        if (strData.length() > maxDataLength)
            throw new Exception("data too large (max 500 chars)");

        type = Type;
        seqnum = SeqNum % SeqNumModulo;
        data = strData;
    }

    // special packet constructors to be used in place of hidden constructor
    public static Packet createACK(int SeqNum) throws Exception {
        return new Packet(0, SeqNum, new String());
    }

    public static Packet createPacket(int SeqNum, String data) throws Exception {
        return new Packet(1, SeqNum, data);
    }

    public static Packet createEOT(int SeqNum) throws Exception {
        return new Packet(2, SeqNum, new String());
    }

    ///////////////////////// PACKET DATA //////////////////////////////////////////

    public int getType() {
        return type;
    }

    public int getSeqNum() {
        return seqnum;
    }

    public int getLength() {
        return data.length();
    }

    public byte[] getData() {
        return data.getBytes();
    }

    //////////////////////////// UDP HELPERS ///////////////////////////////////////

    public byte[] getUDPdata() {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        buffer.putInt(type);
        buffer.putInt(seqnum);
        buffer.putInt(data.length());
        buffer.put(data.getBytes(),0,data.length());
        return buffer.array();
    }

    public static Packet parseUDPdata(byte[] UDPdata) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
        int type = buffer.getInt();
        int seqnum = buffer.getInt();
        int length = buffer.getInt();
        byte data[] = new byte[length];
        buffer.get(data, 0, length);
        return new Packet(type, seqnum, new String(data));
    }
}
