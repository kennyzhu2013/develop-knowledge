package com.cmic.recording;

import lombok.Data;

@Data
public class IPPort {
    public String ip;
    public int port;
    public int vedioport;
    public String videoIp;

    public IPPort() {
    }

    public IPPort(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public boolean equals(IPPort other) {
        if (other!=null && ip.equals(other.ip) && port == other.port) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return String.format("IPPort %s:%d", ip, port);
    }
}
