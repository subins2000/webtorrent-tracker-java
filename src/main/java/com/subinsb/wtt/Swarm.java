package com.subinsb.wtt;

import java.util.Vector;

public class Swarm {
    private String infoHash;
    private Vector<Peer> peers = new Vector<Peer>();

    // TODO completed list

    Swarm(String infoHash) {
        this.infoHash = infoHash;
    }

    Vector<Peer> getPeers() {
        return this.peers;
    }

    int getPeersCount() {
        return this.peers.size();
    }

    void addPeer(Peer peer) {
        this.peers.add(peer);
    }

    void removePeer(Peer peer) {
        this.peers.removeElement(peer);
    }
}
