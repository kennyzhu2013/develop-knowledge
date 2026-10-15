package com.cmic.controller;

import com.cmic.pojo.vo.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vipusher")
public class TestController {

    @ResponseBody
    @RequestMapping(value = "/task/callback", method = RequestMethod.POST, produces = "application/json;charset=UTF-8")
    public CommonResult<String> taskCallback(@RequestBody Map<String, Object> params) {
        try {
            //log.info("receive a task callback from VI: {}",  params);
            if (params == null || params.isEmpty()) {
                log.error("task callback params is empty");
                return CommonResult.error("回调处理失败");
            }
            log.info("[{}] - receive a task callback from VI: {}", params.get("taskId"), params);
        } catch (Exception e) {
            log.error("application controller occurs exception: {}", e.getMessage());
            return CommonResult.error("回调处理失败");
        }
        return CommonResult.success("回调处理成功");
    }


}
