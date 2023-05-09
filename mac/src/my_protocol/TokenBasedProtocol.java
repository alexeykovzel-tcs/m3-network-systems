package my_protocol;

import framework.IMACProtocol;
import framework.MediumState;
import framework.TransmissionInfo;
import framework.TransmissionType;

import java.util.*;

/**
 * A fairly trivial Medium Access Control scheme.
 *
 * @author Jaco ter Braak, University of Twente
 * @version 05-12-2013
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
public class TokenBasedProtocol implements IMACProtocol {
    private static final double REQUEST_ID_CHANCE = 0.25;
    private static final int SLOTS_PER_SENDER = 6;
    private final Set<Integer> ids = new HashSet<>();

    private int candidateId = 0;     // id that can be assigned to the node
    private int senderSlots = 0;     // consecutive slots per sender
    private int senderId = 1;        // id reserved for the next sender
    private int id = 0;              // id of the current node

    @Override
    public TransmissionInfo TimeslotAvailable(MediumState previousState, int controlInfo, int queueLength) {
        if (previousState == MediumState.Succes) {
            // increment consecutive slots per sender
            if (controlInfo == senderId) {
                senderSlots++;
            }

            // handle sender id
            ids.add(controlInfo);
            if (candidateId != 0 && candidateId == controlInfo) {
                // assign id to the current node
                id = candidateId;
            }
        }

        // try to assign node id and notify others
        candidateId = 0;
        if (id == 0) {
            if (Math.random() < REQUEST_ID_CHANCE) {
                candidateId = ids.size() + 1;
                return new TransmissionInfo(TransmissionType.NoData, candidateId);
            } else {
                return new TransmissionInfo(TransmissionType.Silent, 0);
            }
        }

        // calculate id for the next sending node
        if (senderSlots >= SLOTS_PER_SENDER || previousState == MediumState.Idle) {
            senderId = senderId % ids.size() + 1;
            senderSlots = 0;
        }

        // send data if it's the node's turn
        return queueLength != 0 && id == senderId
                ? new TransmissionInfo(TransmissionType.Data, id)
                : new TransmissionInfo(TransmissionType.Silent, 0);
    }
}
