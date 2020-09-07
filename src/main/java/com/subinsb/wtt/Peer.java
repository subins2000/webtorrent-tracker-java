package com.subinsb.wtt;

import com.google.gson.Gson;

import org.java_websocket.WebSocket;

import java.util.ArrayList;

public class Peer {
    private WebSocket ws;
    protected String id;
    private ArrayList<String> infoHashes = new ArrayList<String>();

    Peer (WebSocket ws) {
        this.ws = ws;
    }

    void setId(String id) {
        this.id = id;
    }

    void addInfoHash(String infoHash) {
        this.infoHashes.add(infoHash);
    }

    void sendMessage(TrackerMessage tm) {
        Gson gson = new Gson();

        if (tm.action == "announce") {
            Utils.log("d", "aaaaaaaa" + gson.toJson(tm));
        }

        if (this.ws != null) {
            this.ws.send(gson.toJson(tm));
        }
    }

    ArrayList<String> getInfoHashes() {
        return this.infoHashes;
    }

    void removeInfoHash(String infoHash) {
        this.infoHashes.remove(infoHash);
    }
}
