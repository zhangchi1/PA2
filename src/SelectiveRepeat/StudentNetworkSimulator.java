import java.util.LinkedList;
import java.util.Queue;

/**
 * @Auther: Di Zhu
 * @Date: 11-06-2019 11:05
 * @Description:
 */
public class StudentNetworkSimulator extends NetworkSimulator
{
    /*
     * Predefined Constants (static member variables):
     *
     *   int MAXDATASIZE : the maximum size of the Message data and
     *                     Packet payload
     *
     *   int A           : a predefined integer that represents entity A
     *   int B           : a predefined integer that represents entity B
     *
     * Predefined Member Methods:
     *
     *  void stopTimer(int entity):
     *       Stops the timer running at "entity" [A or B]
     *  void startTimer(int entity, double increment):
     *       Starts a timer running at "entity" [A or B], which will expire in
     *       "increment" time units, causing the interrupt handler to be
     *       called.  You should only call this with A.
     *  void toLayer3(int callingEntity, Packet p)
     *       Puts the packet "p" into the network from "callingEntity" [A or B]
     *  void toLayer5(String dataSent)
     *       Passes "dataSent" up to layer 5
     *  double getTime()
     *       Returns the current time in the simulator.  Might be useful for
     *       debugging.
     *  int getTraceLevel()
     *       Returns TraceLevel
     *  void printEventList()
     *       Prints the current event list to stdout.  Might be useful for
     *       debugging, but probably not.
     *
     *
     *  Predefined Classes:
     *
     *  Message: Used to encapsulate a message coming from layer 5
     *    Constructor:
     *      Message(String inputData):
     *          creates a new Message containing "inputData"
     *    Methods:
     *      boolean setData(String inputData):
     *          sets an existing Message's data to "inputData"
     *          returns true on success, false otherwise
     *      String getData():
     *          returns the data contained in the message
     *  Packet: Used to encapsulate a packet
     *    Constructors:
     *      Packet (Packet p):
     *          creates a new Packet that is a copy of "p"
     *      Packet (int seq, int ack, int check, String newPayload)
     *          creates a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and a
     *          payload of "newPayload"
     *      Packet (int seq, int ack, int check)
     *          chreate a new Packet with a sequence field of "seq", an
     *          ack field of "ack", a checksum field of "check", and
     *          an empty payload
     *    Methods:
     *      boolean setSeqnum(int n)
     *          sets the Packet's sequence field to "n"
     *          returns true on success, false otherwise
     *      boolean setAcknum(int n)
     *          sets the Packet's ack field to "n"
     *          returns true on success, false otherwise
     *      boolean setChecksum(int n)
     *          sets the Packet's checksum to "n"
     *          returns true on success, false otherwise
     *      boolean setPayload(String newPayload)
     *          sets the Packet's payload to "newPayload"
     *          returns true on success, false otherwise
     *      int getSeqnum()
     *          returns the contents of the Packet's sequence field
     *      int getAcknum()
     *          returns the contents of the Packet's ack field
     *      int getChecksum()
     *          returns the checksum of the Packet
     *      int getPayload()
     *          returns the Packet's payload
     *
     */

    /*   Please use the following variables in your routines.
     *   int WindowSize  : the window size
     *   double RxmtInterval   : the retransmission timeout
     *   int LimitSeqNo  : when sequence number reaches this value, it wraps around
     */

    public static final int FirstSeqNo = 0;
    private int WindowSize;
    private double RxmtInterval;
    private int LimitSeqNo;

    // Add any necessary class variables here.  Remember, you cannot use
    // these variables to send messages error free!  They can only hold
    // state information for A or B.
    // Also add any necessary methods (e.g. checksum of a String)

    // Sender variables
    private int sndwndbase = 0;          // Head of the window, start with 0
    private int sndSeqNum = 0;           // Sequence number of the next to be sent packet

    private static int maxQueueSize = 50;       // Buffer 50 messages maximum
    private Queue<Message> msgQueue;            // Message buffer queue, FIFO
    LinkedList<Packet> senderBuffer;            // In case we need to retransmit

    private boolean isFirstPacket = true;       // Remove stopTimer warning


    // Receiver variables
    private int rcvwndbase = 0;
    private Packet[] receiverBuffer;


    // Statistics variables
    private int transmittedPackets = 0;
    private int retransmittedPackets = 0;
    private int deliveredPackets = 0;
    private int ackedPackets = 0;
    private int corruptedPackets = 0;


    // This is the constructor.  Don't touch!
    public StudentNetworkSimulator(int numMessages,
                                   double loss,
                                   double corrupt,
                                   double avgDelay,
                                   int trace,
                                   int seed,
                                   int winsize,
                                   double delay) {
        super(numMessages, loss, corrupt, avgDelay, trace, seed);
        WindowSize = winsize;
        LimitSeqNo = winsize * 2; // set appropriately; assumes SR here!
        RxmtInterval = delay;
    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        if (msgQueue.size() >= maxQueueSize) {
            // Message buffer is full, exiting program
            System.out.println("Buffer is full, exiting program!");
            System.exit(1);
        } else {
            msgQueue.add(message);
            if (senderBuffer.size() >= WindowSize) {
                System.out.println("Sender Buffer is full!");
            }
            while (senderBuffer.size() < WindowSize) {
                if (msgQueue.isEmpty()) {
                    return;
                } else {
                    System.out.println("A is sending a packet, seqNum: " + sndSeqNum);
                    Message msg = msgQueue.poll();
                    Packet packet = new Packet(sndSeqNum, -1, 0, msg.getData());
                    packet.setChecksum(checksum(packet));

                    senderBuffer.add(packet);
                    sndSeqNum = updateSeqNum(sndSeqNum);

                    if (isFirstPacket) {
                        isFirstPacket = false;
                    } else {
                        stopTimer(A);
                    }
                    startTimer(A, RxmtInterval);

                    toLayer3(A, packet);
                    transmittedPackets++;
                }
            }
        }
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        if (packet.getChecksum() == checksum(packet)) {
            if (!seqNumInWindow(packet.getAcknum(), sndwndbase)) {
                // Ack number not in window
                if (isDuplicateAck(sndwndbase, packet.getAcknum())) {
                    // Duplicate ACK, retransmit the last unAcked packet
                    System.out.println("A received duplicate ACK, ackNum: " + packet.getAcknum());
                    System.out.println("Retransmitting packet, seqNum: "+ senderBuffer.peek().getSeqnum());

                    stopTimer(A);
                    toLayer3(A, senderBuffer.peek());
                    retransmittedPackets++;
                    startTimer(A, RxmtInterval);
                } else {
                    // Packet fall out of window
                    System.out.println("A received out of window Ack, ackNum: " + packet.getAcknum());
                }
            } else {
                // Ack in window
                System.out.println("A received an Ack, acknum: " + packet.getAcknum());
                while (!senderBuffer.isEmpty()) {
                    if (senderBuffer.peek().getSeqnum() == packet.getAcknum()) {
                        // Packet is acked, remove it from the buffer
                        Packet deleted = senderBuffer.poll();
                        //System.out.println("Deleting packet seqNum " + deleted.getSeqnum()+ " from the buffer");
                        sndwndbase = updateSeqNum(sndwndbase);      // Slide the window
                        break;
                    } else {
                        // Received a cumulative Ack, need to slide window more than 1
                        Packet deleted = senderBuffer.poll();
                        //System.out.println("Deleting packet seqNum " + deleted.getSeqnum()+ " from the buffer");
                        sndwndbase = updateSeqNum(sndwndbase);      // Slide the window
                    }
                }
            }
        } else {
            // Packet is corrupted
            System.out.println("A received corrupted packet from B, ackNum: " + packet.getAcknum());
            corruptedPackets++;
        }
    }

    // This routine will be called when A's timer expires (thus generating a
    // timer interrupt). You'll probably want to use this routine to control
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped.
    protected void aTimerInterrupt() {
        if (senderBuffer.isEmpty()) {
            startTimer(A, RxmtInterval);
        } else {
            System.out.println("Time out, A is retransmitting packet seqNum: " + senderBuffer.peek().getSeqnum());
            toLayer3(A, senderBuffer.peek());
            startTimer(A, RxmtInterval);
            retransmittedPackets++;
        }

    }

    // This routine will be called once, before any of your other A-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        System.out.println("Initializing A");
        senderBuffer = new LinkedList<>();
        msgQueue = new LinkedList<>();
    }

    // This routine will be called whenever a packet sent from the B-side
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
        if (packet.getChecksum() == checksum(packet)) {
            // Packet not corrupted
            if (!seqNumInWindow(packet.getSeqnum(), rcvwndbase)) {
                // Packet not in window
                if (isDuplicateAck(rcvwndbase, packet.getSeqnum())) {
                    // Duplicate Packet
                    System.out.println("B received duplicate pkt, seqNum: " + packet.getSeqnum());
                    Packet ACK = new Packet(getPreviousSeqNum(rcvwndbase), getPreviousSeqNum(rcvwndbase), -1, "");
                    ACK.setChecksum(checksum(ACK));
                    toLayer3(B, ACK);
                    ackedPackets++;
                } else {
                    // Out-of-window packet
                    System.out.println("B received not-in-window pkt, seqNum: " + packet.getSeqnum());
                }
            } else {
                if (packet.getSeqnum() == rcvwndbase) {
                    // Packet is expected
                    //System.out.println("B acking, ackNum: " + rcvwndbase);
                    ackedPackets++;

                    rcvwndbase = updateSeqNum(rcvwndbase);

                    toLayer5(packet.getPayload());
                    deliveredPackets++;

                    //Check buffer for subsequent received packets
                    while (receiverBuffer[rcvwndbase % WindowSize] != null) {
                        Packet oooPkt = receiverBuffer[rcvwndbase % WindowSize];
                        //System.out.println("B acked a buffered packet, seqNum: " + oooPkt.getSeqnum());
                        ackedPackets++;

                        toLayer5(oooPkt.getPayload());
                        deliveredPackets++;

                        receiverBuffer[rcvwndbase % WindowSize] = null;
                        rcvwndbase = updateSeqNum(rcvwndbase);      // Slide the window
                    }

                    // Send Cumulative ACK
                    System.out.println("B acking, ackNum: " + getPreviousSeqNum(rcvwndbase));
                    Packet ack = new Packet(getPreviousSeqNum(rcvwndbase), getPreviousSeqNum(rcvwndbase), -1, "");
                    ack.setChecksum(checksum(ack));
                    toLayer3(B, ack);
                } else {
                    // Packet is not expected but is in window
                    System.out.println("B received not-in-order pkt, seqNum: " + packet.getSeqnum());
                    receiverBuffer[packet.getSeqnum() % WindowSize] = packet;
                    Packet ack = new Packet(packet.getSeqnum(), getPreviousSeqNum(rcvwndbase), -1, "");
                    ack.setChecksum(checksum(ack));
                    toLayer3(B, ack);
                    ackedPackets++;
                }
            }
        } else {
            // Packet is corrupted, wait for A to time out and retransmit
            System.out.println("B received corrputed packet, seqNum: " + packet.getSeqnum());
            corruptedPackets++;
        }
    }

    // This routine will be called once, before any of your other B-side
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        System.out.println("Initializing B");
        receiverBuffer = new Packet[WindowSize];
    }

    // Use to print final statistics
    protected void Simulation_done() {
        double lostRatio = (double)(retransmittedPackets - corruptedPackets) /
                (double)(transmittedPackets + retransmittedPackets + ackedPackets);
        double corruptRatio = (double)(corruptedPackets) / (double)((transmittedPackets + retransmittedPackets)
        + ackedPackets - (retransmittedPackets - corruptedPackets));
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + transmittedPackets);
        System.out.println("Number of retransmissions by A:" + retransmittedPackets);
        System.out.println("Number of data packets delivered to layer 5 at B:" + deliveredPackets);
        System.out.println("Number of ACK packets sent by B:" + ackedPackets);
        System.out.println("Number of corrupted packets:" + corruptedPackets);
        System.out.println("Ratio of lost packets:" + lostRatio );
        System.out.println("Ratio of corrupted packets:" + corruptRatio);
        System.out.println("Average RTT:" + "<YourVariableHere>");
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

    /*** Helper Functions ***/
    protected int checksum(Packet packet) {
        int chksum = packet.getSeqnum() + packet.getAcknum();
        String payload = packet.getPayload();
        for (int i = 0 ; i < payload.length() ; i++) {
            chksum += (int) payload.charAt(i);
        }
        return chksum;
    }

    // Sequence number starts from 0
    protected int updateSeqNum(int seqNum) {
        if (seqNum == LimitSeqNo - 1) {
            return FirstSeqNo;
        } else {
            return seqNum + 1;
        }
    }

    protected int getPreviousSeqNum(int curSeqNum) {
        return curSeqNum == 0 ? LimitSeqNo - 1 : curSeqNum - 1;
    }

    protected int getLastSeqNumOfWindow(int windowHead) {
        if (windowHead + WindowSize <= LimitSeqNo) {
            return windowHead + WindowSize - 1;
        } else {
            return windowHead + WindowSize - LimitSeqNo - 1;
        }
    }

    protected boolean seqNumInWindow(int seqNum, int windowBase) {
        if (seqNum >= LimitSeqNo)
            return false;

        if (getLastSeqNumOfWindow(windowBase) < windowBase) {
            return seqNum >= windowBase || seqNum <= getLastSeqNumOfWindow(windowBase);
        } else {
            return seqNum >= windowBase && seqNum <= getLastSeqNumOfWindow(windowBase);
        }
    }

    protected boolean isDuplicateAck(int curSeqNum, int ackNum) {
        int previousSeqNum = getPreviousSeqNum(curSeqNum);
        if (ackNum == previousSeqNum) {
            return true;
        }
        return false;
    }

}
