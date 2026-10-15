package com.cmic.util;

import com.cmic.constant.OBVConstant;
import com.cmic.constant.VodConstant;
import com.cmic.http.HTTPServer;
import com.cmic.http.handlers.*;

import java.io.IOException;

public class MyHTTPServer extends HTTPServer {


    public MyHTTPServer(String host, int port, int threads) throws IOException {
        super(host, port, threads);
        this.addHandler("/control", new ChangeCodeHandler());
        this.addHandler("/sms_callback", new IdentitySMSHandler());
        this.addHandler(VodConstant.VOD.PLAY_URL, new VodStartHandler());
        this.addHandler(VodConstant.VOD.STOP_URL, new VodStopHandler());
        this.addHandler(VodConstant.VOD.UPDATE_VOD_MEDIA, new VodMediaChangeHandler());
        this.addHandler(VodConstant.VOD.PLAY_BACK_URL, new VodCompleteHandler());
        this.addHandler(VodConstant.VOD.CONTROL_URL, new VodControlHandler());
        this.addHandler(OBVConstant.PLAY_BACK_URL, new OBVCompleteHandler());
        this.addHandler("/triggerSipStackAudit", new TriggerSipStackAuditHandler());
        this.addHandler("/changeStartMode", new ProcessModeHandler());

        this.startServer();
    }

}
