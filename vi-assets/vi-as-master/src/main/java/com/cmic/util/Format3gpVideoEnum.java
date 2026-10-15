package com.cmic.util;

public enum Format3gpVideoEnum {
        ck("540x960", "L30", 11),
        kc("960x540", "L30", 10),
        fb("640x480", "L30", 9),
        bf("480x640", "L30", 8),
        ld("1024x576", "L30", 7),
        dl("576x1024", "L30", 6),
        jb("854x480", "L30", 5),
        ie("800x600", "L30", 4),
        ii("800x800", "L30", 3),
        gg("720x720", "L30", 2),
        fa("640x360", "L30", 1),


        gm("720x1280", "L31", 5),
        on("1920x1080", "L31", 4),
        no("1080x1920", "L31", 3),
        lh("1024x768", "L31", 2),
        kk("960x960", "L31", 1),
        other("not_know", "L30", 0),
        ;
        private final String frameDesc;
        private final String type;
        private final int priorityLevel;
        private Format3gpVideoEnum(String frameDesc, String type, int priorityLevel){
            this.frameDesc = frameDesc;
            this.type = type;
            this.priorityLevel = priorityLevel;
        }

        public String getFrameDesc() {
            return frameDesc;
        }

        public String getType() {
            return type;
        }

        public int getPriorityLevel() {
                return priorityLevel;
        }
}