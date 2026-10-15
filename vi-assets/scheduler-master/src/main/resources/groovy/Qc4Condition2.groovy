package groovy

import org.springframework.context.ApplicationContext

class Qc4Condition2 {
    ApplicationContext applicationContext
    def redisService = applicationContext.getBean("redisService")

    def execute(){
        def qc1Status = redisService.getSubTaskItem("qc1Status")
        def qc2Status = redisService.getSubTaskItem("qc2Status")
        def qc3Status = redisService.getSubTaskItem("qc3Status")

        return qc1Status && qc2Status && qc3Status;
    }
}