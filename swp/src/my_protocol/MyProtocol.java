package my_protocol;

import framework.IRDTProtocol;
import framework.Utils;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version 10-07-2019
 * <p>
 * Copyright University of Twente,  2013-2019
 * <p>
 * *************************************************************************
 * Copyright notice                                                       *
 * *
 * This file may  ONLY  be distributed UNMODIFIED.                        *
 * In particular, a correct solution to the challenge must NOT be posted  *
 * in public places, to preserve the learning effect for future students. *
 * *************************************************************************
 */
public class MyProtocol extends IRDTProtocol {
    private static final int PACKET_SIZE = 128;
    private static final int WINDOW_SIZE = 20;
    private static final int HEADER_SIZE = 1;
    private static final int TIMEOUT = 500;
    private static final int SEQ_RANGE = WINDOW_SIZE * 2;
    private final Map<Integer, Integer[]> transitPackets = new HashMap<>();

    private final Lock lock = new ReentrantLock();
    private final Condition isWindowNotFull = lock.newCondition();
    private final Condition isWindowEmpty = lock.newCondition();

    @Override
    public void sender() {
        // read from the input file
        Integer[] fileContents = Utils.getFileContents(getFileID());

        // listen to packets sent by the client
        var packetListener = new ServerPacketListener();
        new Thread(packetListener).start();

        // check if the file is valid
        if (fileContents != null) {
            System.out.printf("Sending a file of %d bytes...\n", fileContents.length);
            int filePointer = 0; // keep track of where we are in the data
            int seq = 0; // seq of the next packet

            try {
                while (filePointer != fileContents.length) {
                    lock.lock();
                    try {
                        // wait until there is space in SWS
                        while (!packetListener.isAvailableSeq(seq)) {
                            isWindowNotFull.await();
                        }

                        // create a new packet of an appropriate size
                        int packetSize = Math.min(PACKET_SIZE, fileContents.length - filePointer);
                        Integer[] packet = new Integer[HEADER_SIZE + packetSize];

                        // concat a header and the file content into the packet
                        System.arraycopy(fileContents, filePointer, packet, HEADER_SIZE, packetSize);
                        packet[0] = seq;

                        // send the packet to the client
                        sendPacket(packet, seq);
                        seq = (seq + 1) % SEQ_RANGE;

                        filePointer += packetSize;
                        System.out.printf("Sending totally %d bytes...\n", filePointer);
                    } finally {
                        lock.unlock();
                    }
                }
                finishTransmission();
            } catch (InterruptedException e) {
                System.out.println("Failing to send a file...");
            }
        }
    }

    private void finishTransmission() throws InterruptedException {
        lock.lock();
        try {
            // wait until all packets are delivered
            while (transitPackets.size() != 0) {
                isWindowEmpty.await();
            }

            // notify the client that all packets are sent
            Integer[] fin = new Integer[]{200};
            getNetworkLayer().sendPacket(fin);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        int seq = (Integer) tag;
        if (transitPackets.containsKey(seq)) {
            // handle expiration of the timeout
            System.out.println("Timer expired for packet: " + seq);

            // send packet again
            Integer[] packet = transitPackets.get(seq);
            sendPacket(packet, seq);
        }
    }

    /**
     * Thread listening to the acknowledgments sent by the client.
     */
    class ServerPacketListener implements Runnable {
        int firstAck = 0;

        @Override
        public void run() {
            boolean stop = false;
            while (!stop) {
                try {
                    Integer[] packet = getNetworkLayer().receivePacket();

                    // process a packet sent by the client (if any)
                    if (packet != null) {
                        int ack = packet[0];
                        if (transitPackets.containsKey(ack)) {
                            acceptAck(ack);
                            shiftSendWindow();
                        }
                    }

                    // sleep 10 ms until checking new packets
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }

        private void shiftSendWindow() {
            int nextAck = firstAck;
            for (int i = 0; i < WINDOW_SIZE; i++) {
                if (transitPackets.containsKey(nextAck)) break;
                nextAck = (nextAck + 1) % SEQ_RANGE;
            }
            firstAck = nextAck;
        }

        public boolean isAvailableSeq(int seq) {
            if (WINDOW_SIZE == transitPackets.size()) return false;
            int lastAck = (firstAck + WINDOW_SIZE - 1) % SEQ_RANGE;
            return isValidSeq(seq, firstAck, lastAck);
        }
    }

    private synchronized void acceptAck(int ack) {
        lock.lock();
        try {
            transitPackets.remove(ack);
            if (transitPackets.size() == 0) {
                isWindowEmpty.signalAll();
            }
            isWindowNotFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean isValidSeq(int seq, int lowerSeq, int upperSeq) {
        if (upperSeq < lowerSeq) {
            boolean lowerRange = seq >= lowerSeq && seq < SEQ_RANGE;
            boolean upperRange = seq >= 0 && seq <= upperSeq;
            return lowerRange || upperRange;
        } else {
            return seq >= lowerSeq && seq <= upperSeq;
        }
    }

    /**
     * Sends a packet with the given sequence number
     *
     * @param packet packet that is being sent
     * @param seq    sequence of the package
     */
    private synchronized void sendPacket(Integer[] packet, int seq) {
        // send the packet to the network layer
        getNetworkLayer().sendPacket(packet);
        System.out.println("Sending a packet of seq: " + packet[0]);
        transitPackets.put(seq, packet);

        // schedule a timer for 1000 ms into the future
        framework.Utils.Timeout.SetTimeout(TIMEOUT, this, seq);
    }

    @Override
    public synchronized Integer[] receiver() {
        System.out.println("Receiving...");
        int lowestExpectedSeq = 0;

        // create the array that will contain the file contents
        Integer[] fileContents = new Integer[0];

        // store received packets
        Map<Integer, Integer[]> receivedPackets = new HashMap<>();

        // loop until we are done receiving the file
        boolean stop = false;
        while (!stop) {

            // try to receive a packet from the network layer
            Integer[] packet = getNetworkLayer().receivePacket();

            if (packet != null) {
                // check if the packet is FIN
                if (packet.length == 1 && packet[0] == 200) {
                    stop = true;
                    continue;
                }

                // retrieve packet seq
                Integer[] header = Arrays.copyOf(packet, HEADER_SIZE);
                int seq = header[0];

                // tell the user about received packet
                System.out.println("Received packet of seq: " + seq);

                // retrieve packet content
                Integer[] content = new Integer[packet.length - HEADER_SIZE];
                System.arraycopy(packet, HEADER_SIZE, content, 0, content.length);

                // ...
                for (int packetIdx = lowestExpectedSeq; packetIdx < lowestExpectedSeq + WINDOW_SIZE; packetIdx++) {
                    int currentSeq = packetIdx % SEQ_RANGE;

                    // ...
                    if (seq == currentSeq && !receivedPackets.containsKey(packetIdx)) {
                        receivedPackets.put(packetIdx, content);
                    }
                }

                // send ack to a received packet
                getNetworkLayer().sendPacket(header);

                // ...
                if (seq == lowestExpectedSeq) {
                    int lastPacketIdx = lowestExpectedSeq;

                    // ...
                    for (int packetIdx : new TreeSet<>(receivedPackets.keySet())) {
                        Integer[] packetContent = receivedPackets.get(packetIdx);

                        // ...
                        if (packetIdx - lastPacketIdx > 1) break;
                        lastPacketIdx = packetIdx;

                        // store the packet content
                        int dataReceived = fileContents.length;
                        fileContents = Arrays.copyOf(fileContents, dataReceived + packetContent.length);
                        System.arraycopy(packetContent, 0, fileContents, dataReceived, packetContent.length);
                    }

                    // ...
                    for (int i = lowestExpectedSeq; i <= lastPacketIdx; i++) {
                        receivedPackets.remove(i);
                    }

                    // ...
                    lowestExpectedSeq = (lastPacketIdx + 1) % SEQ_RANGE;
                }
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    stop = true;
                }
            }
        }

        // return the output file
        return fileContents;
    }
}