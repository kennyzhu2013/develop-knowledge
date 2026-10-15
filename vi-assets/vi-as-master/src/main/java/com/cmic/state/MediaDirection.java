package com.cmic.state;

public class MediaDirection {

//1-none, 2-sendrecv, 3-sendonly, 4-recvonly, 5-inactive, 6-invalid

    public static final int NONE = 1;
    public static final int SENDRECV = 2;
    public static final int SENDONLY = 3;
    public static final int RECVONLY = 4;
    public static final int INACTIVE = 5;
    public static final int INVALID = 6;

    public static final String DEC_NONE = "none";
    public static final String DEC_SENDRECV = "sendrecv";
    public static final String DEC_SENDONLY = "sendonly";
    public static final String DEC_RECVONLY = "recvonly";
    public static final String DEC_INACTIVE = "inactive";
    public static final String DEC_INVALID = "invalid";


    public static int getDirection(String direction) {
        int nDirection = 0;
        switch (direction) {
            case DEC_NONE:
                nDirection = NONE;
                break;
            case DEC_SENDRECV:
                nDirection = SENDRECV;
                break;
            case DEC_SENDONLY:
                nDirection = SENDONLY;
                break;
            case DEC_RECVONLY:
                nDirection = RECVONLY;
                break;
            case DEC_INACTIVE:
                nDirection = INACTIVE;
                break;
            case DEC_INVALID:
                nDirection = INVALID;
                break;
            default:
                break;
        }
        return nDirection;
    }


    public static void main(String[] args) {

    }
}
