import com.cmic.config.AppConfig;
import com.cmic.pojo.CheckNum;
import com.common.dao.db.DbManager;
import com.dcc.config.cache.BlackListCache;

import com.dcc.config.dao.DccConfigDb;
import com.dcc.config.service.BlackWhiteService;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 测试黑白名单增加长度限制
 *
 * Author: luohuanfa
 * Date: 2025/9/2
 */
public class TestBlackListNumberLength {
    private static  HashMap<Integer, ArrayList<CheckNum>> checkNumHashMap = new HashMap<>();

    public static void main(String[] args) throws Exception {
        System.out.println("初始化数据库");

        HashMap<String, String> config = new HashMap<>();
        System.out.println("===========" + AppConfig.getConfDbPath());
        //config.put(CommonDb.DB_ALIAS_NAME, "D:\\git_home\\GS_PROJECT\\pure_xdtmf\\xdtmf\\config\\" + XdtmfAppConfig.getShardingConfigPath());
        //System.out.println("===========" + XdtmfAppConfig.getAuthDbConfigPath());

        config.put(DccConfigDb.DB_ALIAS_NAME, "D:\\git_home\\GS_PROJECT\\dcc-as\\config\\" + AppConfig.getConfDbPath());
        //config.put(CommonDb2.DB_ALIAS_NAME, "D:\\git_home\\GS_PROJECT\\pure_xdtmf\\xdtmf\\config\\" + XdtmfAppConfig.getShardingConfig2Path());
        //System.out.println("===========" + XdtmfAppConfig.getShardingConfig3Path());
        //config.put(CommonDb3.DB_ALIAS_NAME, "D:\\git_home\\GS_PROJECT\\pure_xdtmf\\xdtmf\\config\\" + XdtmfAppConfig.getShardingConfig3Path());
        //System.out.println("===========" + XdtmfAppConfig.getShardingConfig4Path());
        //config.put(CommonDb4.DB_ALIAS_NAME, "D:\\git_home\\GS_PROJECT\\pure_xdtmf\\xdtmf\\config\\" + XdtmfAppConfig.getShardingConfig4Path());
        DbManager.getInstance().init(config);
        //String initSql = "select 1";
        //int count = CommonDb2.getInstance().executeQueryObject(initSql, ResultSetConvertor.INTEGER);
        //System.out.println("InitSql CommonDb2 success " + count);
        //count = CommonDb3.getInstance().executeQueryObject(initSql, ResultSetConvertor.INTEGER);
        //System.out.println("InitSql CommonDb3 success " + count);
        //count = CommonDb4.getInstance().executeQueryObject(initSql, ResultSetConvertor.INTEGER);
        //System.out.println("InitSql CommonDb4 success " + count);

        //MultiDBService.addBillDB(CommonDb2.getInstance());
        //MultiDBService.addBillDB(CommonDb3.getInstance());
        //MultiDBService.addBillDB(CommonDb4.getInstance());
        //DbAsyncService.getInstance().init();
        // DccConfigService.getInstance().init();
        BlackListCache.reload();
        //System.out.println(BlackWhiteService.isInCustomerCallerBlackList("19802024001", "CS0014", "123456"));
        System.out.println(BlackWhiteService.isInBlackList("19802024001", "CS0014", "1232"));
        System.out.println(BlackWhiteService.isInBlackList("19802024001", "CS0015", "1232"));
        //ArrayList<CheckNum> checkList = NumDao.getInstance().selectAllCheckNums();
        //for (CheckNum num : checkList) {
        //    if (!checkNumHashMap.containsKey(num.getUser_id())) {
        //        ArrayList<CheckNum> check_List = new ArrayList<>();
        //        check_List.add(num);
        //        checkNumHashMap.put(num.getUser_id(), check_List);
        //    } else {
        //        checkNumHashMap.get(num.getUser_id()).add(num);
        //    }
        //}
        //
        //ArrayList<CheckNum> tempList = checkNumHashMap.get(293);
        //if (tempList != null) {
        //    for (CheckNum checkNum : tempList) {
        //        if ("19802021445".startsWith(checkNum.getNum())) {
        //            System.out.println("19802021445 is boce");
        //        }
        //    }
        //}

    }
}
