package com.cmic.business;

import com.cmic.dao.BizNumAuthDo;
import com.cmic.dao.ChannelDo;
import com.cmic.dao.CompanyDo;
import com.cmic.dao.CustomerDo;
import com.cmic.service.AuthConfigService;
import com.cmic.util.BlankAuth;
import lombok.Getter;
import org.checkerframework.checker.units.qual.C;

import java.sql.Timestamp;

/**
 * 语音质检企业配置
 *
 * @author XH
 * @version 1.0
 * @date 2026-03-16
 */
public class ViConf {

    // 企业账户状态-正常
    public static int BIZ_STATE_ACTIVE = 1;
    // 企业账户状态-停用
    public static int BIZ_STATE_SUSPENDED =2;
    // 企业账户状态-注销
    public static int BIZ_STATE_CLOSED = 3;

    @Getter
    CompanyDo company;
    @Getter
    CustomerDo customer;
    @Getter
    ChannelDo channel;

    public ViConf(BizNumAuthDo bizNumAuthDo) {
        int companyId = bizNumAuthDo.getCompanyId();
        int customerId = bizNumAuthDo.getCustomerId();
        if (companyId == -1){
            company = BlankAuth.BLANK_COMPANY;
            customer = BlankAuth.BLANK_CUSTOMER;
            channel = BlankAuth.BLANK_CHANNEL;
            return;
        }
        company = AuthConfigService.getInstance().getCompany(companyId);
        customer = AuthConfigService.getInstance().getCustomer(customerId);
        channel = AuthConfigService.getInstance().getChannel(company.getParentId());
    }

    /**
     * 企业账户状态需要三个层级的状态都是正常才是正常
     * @return 账户状态可用：true 账户状态不可用：false
     */
    public boolean isBizStateActive() {
        return BIZ_STATE_ACTIVE == company.getStatus() && BIZ_STATE_ACTIVE == customer.getStatus();
    }

    // 返回影响接续的企业层级状态
    public int getAccountType() {
        if(isBizStateActive()){
            return BIZ_STATE_ACTIVE;
        } else {
            return BIZ_STATE_ACTIVE == company.getStatus() ? customer.getStatus()  : company.getStatus();
        }
    }
}
