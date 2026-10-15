package com.cmic.pojo;

import lombok.Getter;

public enum RecordType {
    MP3(0, "mp3", 2),
    WAV(1, "wav", 1);
    @Getter
    private final int value;
    @Getter
    private final String name;
    @Getter
    private final int billValue;

    RecordType(int value, String name, int billValue) {
        this.value = value;
        this.name = name;
        this.billValue = billValue;
    }

    public static RecordType recordTypeOf(String str) {
        if (WAV.getName().equalsIgnoreCase(str)) {
            return WAV;
        }
        if (MP3.getName().equalsIgnoreCase(str)) {
            return MP3;
        }
        return MP3;
    }

    public static RecordType recordTypeOfInt(int str) {
        if (WAV.getValue() == str) {
            return WAV;
        }
        if (MP3.getValue() == str) {
            return MP3;
        }
        return MP3;
    }
}
