package ns.tcphack;

import java.util.Arrays;

class MyTcpHandler extends TcpHandler {
    public static void main(String[] args) {
        new MyTcpHandler();
    }

    private int[] buildHeader(int len, int[]... fields) {
        int offset = 0;
        int[] header = new int[len];
        for (int[] field : fields) {
            System.arraycopy(field, 0, header, offset, field.length);
            offset += field.length;
        }
        return header;
    }

    private int[] getIpHeader(int payload) {
        int[] firstRow = new int[]{0x60, 0x00, 0xe1, 0xd7};
        int[] secondRow = new int[]{0x00, payload, 0xfd, 0x3f}; // payload is 24 or 42
        int[] srcAddr = new int[]{0x20, 0x01, 0x06, 0x7c, 0x25, 0x64, 0xa3,
                0x11, 0x20, 0x04, 0xbe, 0xf1, 0xbb, 0xea, 0xa0, 0x40};
        int[] dstAddr = new int[]{0x20, 0x01, 0x06, 0x10, 0x19, 0x08, 0xff,
                0x02, 0xf1, 0xb8, 0xf9, 0x1e, 0xfc, 0x14, 0xb7, 0xc7};
        return buildHeader(40, firstRow, secondRow, srcAddr, dstAddr);
    }

    private int getFlags(boolean syn, boolean ack, boolean fin) {
        byte flags = 0b00000000;
        if (syn) flags |= 1 << 1;
        if (ack) flags |= 1 << 4;
        if (fin) flags |= 1;
        return flags;
    }

    private int[] getTcpHeader(int[] seq, int[] ack, int flagByte) {
        int[] srcPort = new int[]{0x1f, 0x92}; // 8081
        int[] dstPort = new int[]{0x1e, 0x1e}; // 7710
        int[] flags = new int[]{0x60, flagByte};
        int[] window = new int[]{0x01, 0xf5}; // 501
        int[] checksum = new int[]{0x00, 0x00}; // invalid
        int[] reserved = new int[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        return buildHeader(24, srcPort, dstPort, seq, ack, flags, window, checksum, reserved);
    }

    private void sendPacket(int[] seq, int[] ack, boolean doSyn, boolean doAck, boolean doFin) {
        int[] packet = new int[64];

        // attach IPv6 & TCP headers
        int flags = getFlags(doSyn, doAck, doFin);
        System.arraycopy(getIpHeader(24), 0, packet, 0, 40);
        System.arraycopy(getTcpHeader(seq, ack, flags), 0, packet, 40, 24);

        // send the packet
        sendData(packet);
    }

    private void requestPage(int[] ack) {
        int headerLen = 64;
        String request = "GET / HTTP/1.0\r\n\r\n";
        int[] packet = new int[headerLen + request.length()];

        // attach IPv6 & TCP headers
        int[] seq = new int[]{0x00, 0x00, 0x00, 0x01};
        int flags = getFlags(false, true, false);
        System.arraycopy(getIpHeader(42), 0, packet, 0, 40);
        System.arraycopy(getTcpHeader(seq, ack, flags), 0, packet, 40, 24);

        // attach HTTP request
        byte[] bytes = request.getBytes();
        for (int i = 0; i < request.length(); i++) {
            packet[headerLen + i] = bytes[i];
        }

        // send the packet
        sendData(packet);
    }

    public MyTcpHandler() {
        super();
        boolean hasRequested = false;
        int[] zeroByte = new int[]{0x00, 0x00, 0x00, 0x00};
        sendPacket(zeroByte, zeroByte, true, false, false);

        while (true) {
            // check for reception of a packet, but wait at most 500 ms:
            int[] receivedPacket = this.receiveData(500);

            if (receivedPacket.length == 0) {
                System.out.print("\nNothing...");
                continue;
            }

            // print the received bytes
            int byteCount = receivedPacket.length;
            System.out.printf("\nReceived %d bytes: ", byteCount);

            int[] seq = new int[4];
            int[] ack = new int[4];
            System.arraycopy(receivedPacket, 44, seq, 0, 4);
            System.arraycopy(receivedPacket, 48, ack, 0, 4);

            // request page
            if (!hasRequested) {
                seq[3] += 1;
                requestPage(seq);
                hasRequested = true;
            }

            // handle closing connection
            if ((receivedPacket[53] & 0b1) == 1) {
                seq[3] += 1;
                sendPacket(ack, seq, false, true, true);
                System.out.println("Closing connection...");
            }

            // print received bytes
            for (int b : receivedPacket) {
                System.out.print(b + " ");
            }
        }
    }
}
