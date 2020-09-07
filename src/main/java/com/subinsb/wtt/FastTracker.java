package com.subinsb.wtt;

import org.java_websocket.WebSocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

class Offer {
    String type = "offer";
    String sdp;
}

class OffersItem {
    Offer offer;
    String offer_id;
}

class Answer {
    String type = "answer";
    String sdp;
}

// Culminated JSON requests & response object
// This is bad
// TODO fix this
class TrackerMessage {
    ArrayList<OffersItem> offers;
    Offer offer;

    Integer complete = 0;
    Integer interval = 0;
    Integer incomplete = 0;
    Integer numwant = 0;

    String action;
    Answer answer;
    String event;
    String info_hash;
    String offer_id;
    String peer_id;
    String to_peer_id;
}

public class FastTracker {
    HashMap<String, Swarm> swarms = new HashMap<String, Swarm>();
    HashMap<String, Peer> peers = new HashMap<String, Peer>();

    int announceInterval = 120;
    int maxOffers = 20;

    FastTracker(int maxOffers, int announceInterval) {
        this.maxOffers = maxOffers;
        this.announceInterval = announceInterval;
    }

    FastTracker() { }

    void processMessage(TrackerMessage tm, Peer peer) throws TrackerException {
        String action = tm.action;
        if (action.equals("announce")) {
            String event = tm.event;
            if (event == null) {
                if (tm.answer == null) {
                    this.processAnnounce(tm, peer);
                } else {
                    this.processAnswer(tm, peer);
                }
            } else if (event.equals("started")) {
                this.processAnnounce(tm, peer);
            } else if (event.equals("stopped")) {
                    // this->processStop(data, peer);
            } else if (event.equals("completed")) {
                // TODO implemenet complete funcs
                // this.processAnnounce(tm, peer);
            } else {
                throw new TrackerException("unknown announce event");
            }
        }
    }

    void disconnectPeer(Peer peer) {
        if (peer.id == null) {
            return;
        }

        Iterator<String> it = peer.getInfoHashes().iterator();
        while (it.hasNext()) {
            String infoHash = it.next();
            Swarm swarm = this.swarms.get(infoHash);
            swarm.removePeer(peer);

            if (swarm.getPeersCount() == 0) {
                this.swarms.remove(infoHash);
                swarm = null; // collect the garbade GC!
            }
        }

        this.peers.remove(peer.id);
    }

    private void processAnnounce(TrackerMessage tm, Peer peer) throws TrackerException {
        if (tm.peer_id == null) {
            throw new TrackerException("announce: peer_id field is missing or wrong");
        }
        if (tm.info_hash == null) {
            throw new TrackerException("announce: info_hash field is missing or wrong");
        }

        String infoHash = tm.info_hash;
        String peerId = tm.peer_id;
        Swarm swarm = null;

        if (peer.id == null) {
            // New socket with no peer attached
            peer.setId(peerId);

            // Peer has changed of socket
            if (this.peers.containsKey(peerId)) {
                this.disconnectPeer(this.peers.get(peerId));
            }

            this.peers.put(peerId, peer);
        } else if (!peer.id.equals(peerId)) {
            throw new TrackerException("announce: different peer_id on the same connection");
        } else {
            swarm = this.swarms.get(infoHash);
        }

        if (swarm == null) {
            swarm = this.addPeerToSwarm(peer, infoHash);
        } else {
            // TODO completed
        }

        // Response
        TrackerMessage tr = new TrackerMessage();
        tr.action = "announce";
        tr.interval = this.announceInterval;
        tr.info_hash = infoHash;
        tr.complete = 0; // TODO completed
        tr.incomplete = 0; // TODO incomplete

        peer.sendMessage(tr);

        Vector<Peer> swarmPeers = swarm.getPeers();

        if (swarmPeers != null)
            this.sendOffersToPeers(tm, swarm.getPeers(), peer, infoHash);
    }

    void processAnswer(TrackerMessage tm, Peer peer) throws TrackerException {
        if (tm.to_peer_id == null) {
            throw new TrackerException("announce: to_peer_id field is missing or wrong");
        }

        String toPeerId = tm.to_peer_id;

        Peer toPeer = this.peers.get(toPeerId);
        if (toPeer == null) {
            throw new TrackerException("answer: to_peer_id is not in the swarm");
        }

        Utils.log("d", "bbbbbbbb" + tm.answer.sdp);

        // Response
        TrackerMessage tr = tm;
        tr.peer_id = peer.id;

        tr.to_peer_id = null;
        tr.complete = null;
        tr.interval = null;
        tr.incomplete = null;
        tr.numwant = null;

        toPeer.sendMessage(tr);
    }

    Swarm addPeerToSwarm(Peer peer, String infoHash) {
        Swarm swarm = this.swarms.get(infoHash);

        if (swarm == null) {
            swarm = new Swarm(infoHash);
            this.swarms.put(infoHash, swarm);
        }

        swarm.addPeer(peer);
        peer.addInfoHash(infoHash);

        return swarm;
    }

    void sendOffersToPeers(TrackerMessage tm, Vector<Peer> peers, Peer peer, String infoHash) throws TrackerException {
        // Check if there are other peeers
        if (peers.size() <= 1) {
            return;
        }

        // Offers
        if (tm.offers.size() == 0) {
            return;
        }

        if (tm.numwant == 0) {
            return;
        }

        ArrayList<OffersItem> offers = tm.offers;
        int numwant = tm.numwant;

        int countPeersToSend = peers.size() - 1;
        int countOffersToSend = Math.min(
                countPeersToSend,
                Math.min(
                        offers.size(),
                        Math.min(
                                this.maxOffers,
                                numwant
                        )
                )
        );

        if (countOffersToSend == countPeersToSend) {
            // We have offers for all the peers from the swarm - send offers to all
            int i = 0;
            Iterator<Peer> it = peers.iterator();
            while (it.hasNext()) {
                Peer toPeer = it.next();
                if (toPeer != peer) {
                    this.sendOffer(offers.get(i), peer.id, toPeer, infoHash);
                    i++;
                }
            }
        } else {
            // Send offer to random peers
            int min = 0;
            int max = peers.size() - 1;

            Random r = new Random();
            int peerIndex = r.nextInt((max - min) + 1) + min;

            for (int i = 0; i < countOffersToSend; i++) {
                Peer toPeer = peers.get(peerIndex);

                if (toPeer == peer) {
                    i--;
                } else {
                    this.sendOffer(offers.get(i), peer.id, toPeer, infoHash);
                }

                peerIndex++;
                if (peerIndex == max) {
                    peerIndex = 0;
                }
            }
        }
    }

    void sendOffer(OffersItem oi, String fromPeerId, Peer toPeer, String infoHash) throws TrackerException {
        if (oi.offer == null) {
            throw new TrackerException("announce: wrong offer item format");
        }

        Offer offer = oi.offer;
        String offerId = oi.offer_id;

        if (offer.sdp == null) {
            throw new TrackerException("announce: wrong offer item field format");
        }

        TrackerMessage tr = new TrackerMessage();
        tr.action = "announce";
        tr.info_hash = infoHash;
        tr.offer_id = offerId;
        tr.peer_id = fromPeerId;

        tr.offer = new Offer();
        tr.offer.sdp = offer.sdp;

        toPeer.sendMessage(tr);
    }
}
