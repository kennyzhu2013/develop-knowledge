package com.cmic.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class ApplicationInit {

    @Autowired
    private CompanyTabManager companyTabManager;
    @Autowired
    private CustomerTabManager customerTabManager;
    @Autowired
    private FileDownHostManager fileDownHostManager;

    @PostConstruct
    public void applicationInit() {
        //初始化数据源
        try {
            log.info("Project Init ...");
            companyTabManager.init();
            customerTabManager.init();
            fileDownHostManager.init();

        } catch (Exception e) {
            log.error("StartFail", e);
            System.exit(1);
        }
    }

}
