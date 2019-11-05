package GoBackN;

import java.util.*;
import java.io.*;

public class StudentNetworkSimulator extends NetworkSimulator {
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
    private ArrayList<Packet> buffer;
    private int maxBufferSize;
    private int nextSequenceNum;
    private int sequenceBase;
    private int expectedSequenceNum;


    /**
     * statistic variables
     */
    private int transmittedPacketNum = 0;
    private int retransmittedPacketNum = 0;
    private int corruptedPacketNum = 0;
    private int lostPacketNum = 0;
    private int successfullyReceivedPacketNum = 0;
    private int ackNum = 0;

    // RTTs
    private int RTTNums = 0;
    private double totalRTT = 0.0;
    private double startRTT = 0.0;


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

    /**
     * compute the checksum of packet
     *
     * @param seqNum
     * @param ackNum
     * @param payload
     * @return
     */
    private int computeCheckSum(int seqNum, int ackNum, String payload) {
        int checkSum = 0;
        checkSum += seqNum;
        checkSum += ackNum;
        for (int i = 0; i < payload.length(); i++) {
            checkSum += (int) payload.charAt(i);
        }
        return checkSum;
    }

    /**
     * create a new packet
     *
     * @param seqNum
     * @param ackNum
     * @param newPayload
     * @return
     */
    private Packet createPacket(int seqNum, int ackNum, String newPayload) {
        int checkSum = this.computeCheckSum(seqNum, ackNum, newPayload);
        return new Packet(seqNum, ackNum, checkSum, newPayload);
    }

    /**
     * send all packets in the window size
     */
    private void sendNextPacketFromA() {
        while (this.nextSequenceNum < this.sequenceBase + this.WindowSize) {
            if (this.nextSequenceNum >= this.buffer.size()) {
                break;
            }
            // send packet
            System.out.println("SIDE A: Sending packet No." + this.nextSequenceNum);
            toLayer3(A, this.buffer.get(this.nextSequenceNum));
            this.nextSequenceNum += 1;
            if (this.sequenceBase == this.nextSequenceNum) {
                System.out.println("Start Timer ");
                startTimer(A, this.RxmtInterval);
            }
        }

    }

    /**
     * return true if packet is corrupted by checking the checksum
     *
     * @param packet
     * @return
     */
    private boolean isCorruptedPacket(Packet packet) {
        int computedCheckSum = this.computeCheckSum(packet.getSeqnum(), packet.getAcknum(), packet.getPayload());
        return computedCheckSum != packet.getChecksum();

    }

    // This routine will be called whenever the upper layer at the sender [A]
    // has a message to send.  It is the job of your protocol to insure that
    // the data in such a message is delivered in-order, and correctly, to
    // the receiving upper layer.
    protected void aOutput(Message message) {
        int bufferSize = this.sequenceBase + this.WindowSize + this.maxBufferSize;
        if (this.nextSequenceNum < bufferSize) {
            // add packet into the buffer: A is ack num
            Packet packet = this.createPacket(this.nextSequenceNum, A, message.getData());
            // within the window size
            if (this.nextSequenceNum <= this.WindowSize) {
                this.buffer.set(this.nextSequenceNum, packet);
            } else{
                this.buffer.set(this.nextSequenceNum % this.LimitSeqNo, packet);
            }
            this.sendNextPacketFromA();
            System.out.println("SIDE A: received a message ");
            this.transmittedPacketNum += 1;
        } else {
            System.out.println("SIDE A: window size is full!");
        }


    }

    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by a B-side procedure)
    // arrives at the A-side.  "packet" is the (possibly corrupted) packet
    // sent from the B-side.
    protected void aInput(Packet packet) {
        double currRTT = this.getTime() - this.startRTT;
        this.totalRTT += currRTT;
        this.RTTNums += 1;
        // receive packet from B
        this.successfullyReceivedPacketNum += 1;

        if (!this.isCorruptedPacket(packet)) {
            System.out.println("Receiving packet from SIDE B ");
            // update sequence number
            this.sequenceBase = packet.getAcknum() + 1;
            if (this.sequenceBase == this.nextSequenceNum) {
                this.stopTimer(A);
            } else {
                this.startTimer(A, this.RxmtInterval);
            }

        } else {
            // packet is corrupted
            this.corruptedPacketNum += 1;
        }


    }

    // This routine will be called when A's timer expires (thus generating a 
    // timer interrupt). You'll probably want to use this routine to control 
    // the retransmission of packets. See startTimer() and stopTimer(), above,
    // for how the timer is started and stopped. 
    protected void aTimerInterrupt() {
        // start timer for retransmission
        startTimer(A, this.RxmtInterval);

        for (int i = this.sequenceBase;i<this.nextSequenceNum;i++) {

            if (i>this.WindowSize) {
                toLayer3(A,this.buffer.get(i%this.LimitSeqNo));
            } else {
                toLayer3(A,this.buffer.get(i));
            }
            this.retransmittedPacketNum += 1;
            this.transmittedPacketNum += 1;

        }


    }

    // This routine will be called once, before any of your other A-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity A).
    protected void aInit() {
        this.buffer = new ArrayList<>();
        // init empty packets
        for(int i=0;i<this.LimitSeqNo;i++) {
            this.buffer.add(new Packet(0,0,1,"empty"));
        }

        this.WindowSize = 8;
        this.nextSequenceNum = 0;
        this.sequenceBase = 0;
        //maximum number of buffers available at your sender (say for 50 messages)
        this.maxBufferSize = 50;

    }

    // This routine will be called whenever a packet sent from the B-side 
    // (i.e. as a result of a toLayer3() being done by an A-side procedure)
    // arrives at the B-side.  "packet" is the (possibly corrupted) packet
    // sent from the A-side.
    protected void bInput(Packet packet) {
        if (!this.isCorruptedPacket(packet)&&packet.getSeqnum()==this.expectedSequenceNum) {
            toLayer5(packet.getPayload());
            this.ackNum += 1;
            this.expectedSequenceNum += 1;
        } else{
            if(this.isCorruptedPacket(packet)) {
                this.corruptedPacketNum += 1;
            }
        }
        // send ACK to A-side
        int seqNum = this.expectedSequenceNum;
        int ackNum = packet.getSeqnum();
        Packet ackPacket = this.createPacket(seqNum,ackNum,"ack: "+ seqNum);
        toLayer3(B, ackPacket);
        // transmit from B to A
//        this.transmittedPacketNum += 1;
    }

    // This routine will be called once, before any of your other B-side 
    // routines are called. It can be used to do any required
    // initialization (e.g. of member variables you add to control the state
    // of entity B).
    protected void bInit() {
        this.expectedSequenceNum = 0;

    }

    // Use to print final statistics
    protected void Simulation_done() {
        this.lostPacketNum = this.transmittedPacketNum-this.successfullyReceivedPacketNum;
        // TO PRINT THE STATISTICS, FILL IN THE DETAILS BY PUTTING VARIBALE NAMES. DO NOT CHANGE THE FORMAT OF PRINTED OUTPUT
        System.out.println("\n\n===============STATISTICS=======================");
        System.out.println("Number of original packets transmitted by A:" + this.transmittedPacketNum);
        System.out.println("Number of retransmissions by A:" +this.retransmittedPacketNum);
        System.out.println("Number of data packets delivered to layer 5 at B:" + this.successfullyReceivedPacketNum);
        System.out.println("Number of ACK packets sent by B:" + this.ackNum);
        System.out.println("Number of corrupted packets:" + this.corruptedPacketNum);
        // ratios
        System.out.println("Ratio of lost packets:" + this.lostPacketNum/this.transmittedPacketNum);
        System.out.println("Ratio of corrupted packets:" + this.corruptedPacketNum / this.transmittedPacketNum);
        // averages
        System.out.println("Average RTT:" + this.totalRTT / this.RTTNums);
        System.out.println("Average communication time:" + "<YourVariableHere>");
        System.out.println("==================================================");

        // PRINT YOUR OWN STATISTIC HERE TO CHECK THE CORRECTNESS OF YOUR PROGRAM
        System.out.println("\nEXTRA:");
        // EXAMPLE GIVEN BELOW
        //System.out.println("Example statistic you want to check e.g. number of ACK packets received by A :" + "<YourVariableHere>");
    }

}
