package my_protocol;

import framework.IMACProtocol;
import framework.MediumState;
import framework.TransmissionInfo;
import framework.TransmissionType;

import java.util.*;

public class ReserveToSendProtocol implements IMACProtocol {
    // the number of unsent packets, after which the node will request to send them in a row(extra turns)
    public static int PACKET_LIMIT = 6;

    // defines the number of unsent packets, which will be sent in a row
    public static double RECOVERY_PERCENT = 0.0;

    // number of packets to send in a row if a limit exceeded
    public static int RECOVERY_PACKET_NUMBER = 6;

    private final Set<Integer> ids = new HashSet<>();
    private int sequentTurns = 0;
    private int candidateId = 0;
    private int senderId = 1;
    private int id = 0;

    @Override
    public TransmissionInfo TimeslotAvailable(MediumState previousState, int controlInfo, int queueLength) {
        if (previousState == MediumState.Succes) {
            if(String.valueOf(controlInfo).length() == 1) {
                ids.add(controlInfo);
                if (candidateId != 0 && candidateId == controlInfo) {
                    id = candidateId;
                }
            }
        }

        // try to assign id to itself and notify other nodes
        candidateId = 0;
        if (id == 0) {
            if (Math.random() < 0.5) {
                candidateId = ids.size() + 1;
                return new TransmissionInfo(TransmissionType.NoData, candidateId);
            } else {
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }

        // see if there is a special request for multiple sequent turns
        if (previousState == MediumState.Succes && String.valueOf(controlInfo).length() >= 2) {
            String message = String.valueOf(controlInfo);
            sequentTurns = Integer.parseInt(message.substring(1));
        }

        // in case there are no extra turns for one of the node pass a token to a next node
        if (sequentTurns == 0) {
            // calculate id of the next sending node
            int id = Integer.parseInt(String.valueOf(String.valueOf(controlInfo).charAt(0)));
            int lastId = previousState != MediumState.Idle ? id : senderId;

            senderId = lastId % ids.size() + 1;
        }

        // reserve turns for transmitting big amount of data(send request for extra turns)
        // the node can reserve extra turns only when it is its turn
        if (queueLength > PACKET_LIMIT && id == senderId && sequentTurns == 0 && ids.size() != 1) {
            String message = String.valueOf(id);
            int requiredTurns;

            if (RECOVERY_PERCENT != 0.0) {
                requiredTurns = (int) (queueLength * RECOVERY_PERCENT);
            } else {
                requiredTurns = RECOVERY_PACKET_NUMBER;
            }

            message += String.valueOf(requiredTurns); // turns required
            return new TransmissionInfo(TransmissionType.NoData, Integer.parseInt(message));
        }

        // 1 turn passes(unable to send more than one special request in a row*)
        if (sequentTurns != 0) {
            sequentTurns--;
        }

        // send data if the node's turn
        return queueLength != 0 && id == senderId
                ? new TransmissionInfo(TransmissionType.Data, id)
                : new TransmissionInfo(TransmissionType.Silent, 0);
    }
}