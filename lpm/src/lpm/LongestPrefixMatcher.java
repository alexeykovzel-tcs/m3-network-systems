/**
 * LongestPrefixMatcher.java
 * <p>
 * Version: 2019-07-10
 * Copyright: University of Twente,  2015-2019
 * <p>
 * *************************************************************************
 * Copyright notice                            *
 * *
 * This file may  ONLY  be distributed UNMODIFIED.             *
 * In particular, a correct solution to the challenge must NOT be posted  *
 * in public places, to preserve the learning effect for future students. *
 * *************************************************************************
 */

package lpm;

import java.util.*;

public class LongestPrefixMatcher {
    // variable storing maps of ip prefixes with their ports according to the prefix length
    private final List<Map<Integer, Integer>> routes = new ArrayList<>();

    /**
     * You can use this function to initialize variables.
     */
    public LongestPrefixMatcher() {
        for (int i = 32; i >= 0; i--) {
            routes.add(new HashMap<>());
        }
    }

    /**
     * Looks up an IP address in the routing tables
     *
     * @param ip The IP address to be looked up in integer representation
     * @return The port number this IP maps to
     */
    public int lookup(int ip) {
        for (byte i = 32; i > 0; i--) {
            // get ip prefix of the longest possible value
            int prefix = ip >> 32 - i;

            // check if there is such ip prefix pointing to the next node
            if (routes.get(i).containsKey(prefix)) {

                // return port number of the given ip prefix
                return routes.get(i).get(prefix);
            }
        }

        // return -1 if did not find any matches
        return -1;
    }

    /**
     * Adds a route to the routing tables
     *
     * @param ip           The IP the block starts at in integer representation
     * @param prefixLength The number of bits indicating the network part
     *                     of the address range (notation ip/prefixLength)
     * @param portNumber   The port number the IP block should route to
     */
    public void addRoute(int ip, byte prefixLength, int portNumber) {
        // get ip prefix by shifting its binary representation to the right by prefix length
        int prefix = ip >> 32 - prefixLength;

        // store ip prefix pointing to its corresponding port number
        routes.get(prefixLength).put(prefix, portNumber);
    }

    /**
     * This method is called after all routes have been added.
     * You don't have to use this method but can use it to sort or otherwise
     * organize the routing information, if your datastructure requires this.
     */
    public void finalizeRoutes() {
        // TODO: Optionally do something     
    }

    /**
     * Converts an integer representation IP to the human readable form
     *
     * @param ip The IP address to convert
     * @return The String representation for the IP (as xxx.xxx.xxx.xxx)
     */
    private String ipToHuman(int ip) {
        return (ip >> 24 & 0xff) + "." + (ip >> 16 & 0xff) + "." + (ip >> 8 & 0xff) + "." + (ip & 0xff);
    }

    /**
     * Parses an IP
     *
     * @param ipString The IP address to convert
     * @return The integer representation for the IP
     */
    private int parseIP(String ipString) {
        String[] ipParts = ipString.split("\\.");

        int ip = 0;
        for (int i = 0; i < 4; i++) {
            ip |= Integer.parseInt(ipParts[i]) << (24 - (8 * i));
        }

        return ip;
    }
}
