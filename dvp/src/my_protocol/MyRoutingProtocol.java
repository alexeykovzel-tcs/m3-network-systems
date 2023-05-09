package my_protocol;

import framework.*;

import java.util.*;

/**
 * @version 12-03-2019
 * <p>
 * Copyright University of Twente, 2013-2019
 * <p>
 * *************************************************************************
 * Copyright notice                            *
 * *
 * This file may ONLY be distributed UNMODIFIED.              *
 * In particular, a correct solution to the challenge must NOT be posted  *
 * in public places, to preserve the learning effect for future students. *
 * *************************************************************************
 */
public class MyRoutingProtocol implements IRoutingProtocol {
    private final HashMap<Integer, Integer[]> neighborCosts = new HashMap<>();
    private final HashMap<Integer, MyRoute> routingTable = new HashMap<>();
    private final Set<Integer> updatedNeighbors = new HashSet<>();
    private Set<Integer> neighbors = new HashSet<>();
    private LinkLayer linkLayer;

    @Override
    public void init(LinkLayer linkLayer) {
        this.linkLayer = linkLayer;
    }

    @Override
    public void tick(PacketWithLinkCost[] packetsWithLinkCosts) {
        // get the address of this node
        int myAddr = this.linkLayer.getOwnAddress();

        // process the incoming packets from node's neighbors
        for (PacketWithLinkCost packetWithLinkCost : packetsWithLinkCosts) {
            processPacket(packetWithLinkCost);
        }

        // update/save neighbors that broadcast their packets
        for (int neighbor : neighbors) {
            if (!updatedNeighbors.contains(neighbor)) {
                routingTable.remove(neighbor);
                neighborCosts.remove(neighbor);
            }
        }

        // reset node's neighbors
        neighbors = new HashSet<>(updatedNeighbors);
        updatedNeighbors.clear();

        // broadcast packet with node's best routes
        resetRoutingTable();
        Packet packet = new Packet(myAddr, 0, getBestRoutes());
        this.linkLayer.transmit(packet);
    }

    /**
     * Process the incoming packet with link cost.
     *
     * @param packetsWithLinkCost incoming packet with link cost
     */
    private void processPacket(PacketWithLinkCost packetsWithLinkCost) {
        // retrieve the packet's data from the neighbor
        Packet packet = packetsWithLinkCost.getPacket();
        DataTable dataTable = packet.getDataTable();
        int sourceAddr = packet.getSourceAddress();
        int linkCost = packetsWithLinkCost.getLinkCost();
        int myAddr = linkLayer.getOwnAddress();
        updatedNeighbors.add(sourceAddr);

        // update the next hopes for this neighbor
        Integer[] costs = dataTable.getRow(0);
        Integer[] nextHopes = dataTable.getRow(1);
        Integer[] totalCosts = new Integer[6];
        for (int i = 0; i < 6; i++) {
            totalCosts[i] = (costs[i] != -1 && nextHopes[i] != myAddr)
                    ? costs[i] + linkCost : -1;

            // set total destination costs through that neighbor
            neighborCosts.put(sourceAddr, totalCosts);
        }
    }

    /**
     * Reset the routing table of the broadcasting node.
     */
    private void resetRoutingTable() {
        routingTable.clear();
        int myAddr = linkLayer.getOwnAddress();

        // go through all the node's neighbors
        for (Map.Entry<Integer, Integer[]> entry : neighborCosts.entrySet()) {
            int nextHope = entry.getKey();
            Integer[] linkCosts = entry.getValue();

            // go through all the neighbor's destinations
            for (int i = 0; i < 6; i++) {
                int destAddr = i + 1;
                int linkCost = linkCosts[i];

                // check if the address and cost are valid
                if (destAddr != myAddr && linkCost != -1) {
                    MyRoute oldRoute = routingTable.get(destAddr);

                    // substitute the old route if the delivered route is better
                    if (oldRoute == null || oldRoute.cost > linkCost) {
                        routingTable.put(destAddr, buildRoute(nextHope, linkCost));
                    }
                }
            }
        }
    }

    /**
     * Return the data table of the next hopes including their costs for broadcasting.
     *
     * @return data table of the next hopes
     */
    private DataTable getBestRoutes() {
        DataTable routeTable = new DataTable(6);
        int myAddr = linkLayer.getOwnAddress();

        // go through all the destination addresses in the network
        for (int col = 0; col < 6; col++) {
            int destAddr = col + 1;
            MyRoute route = routingTable.get(destAddr);

            // get the next hope address and its cost
            int cost = (myAddr == destAddr) ? 0 : -1;
            int nextHope = -1;
            if (route != null) {
                cost = route.cost;
                nextHope = route.nextHop;
            }

            // set values on the 1-st and the 2nd row correspondingly
            routeTable.set(0, col, cost);
            routeTable.set(1, col, nextHope);
        }
        return routeTable;
    }

    /**
     * Build route by the next hope and the cost.
     *
     * @param nextHope the next hope of the route
     * @param cost     the route cost
     * @return route
     */
    private MyRoute buildRoute(int nextHope, int cost) {
        MyRoute route = new MyRoute();
        route.nextHop = nextHope;
        route.cost = cost;
        return route;
    }

    /**
     * This code extracts from your routing table the forwarding table.
     *
     * @return forwarding table to the server to validate and score your protocol.
     */
    public Map<Integer, Integer> getForwardingTable() {
        HashMap<Integer, Integer> forwardingTable = new HashMap<>();
        for (Map.Entry<Integer, MyRoute> entry : routingTable.entrySet()) {
            forwardingTable.put(entry.getKey(), entry.getValue().nextHop);
        }
        return forwardingTable;
    }
}
