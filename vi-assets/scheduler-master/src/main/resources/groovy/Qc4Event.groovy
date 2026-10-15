package groovy

import org.springframework.context.ApplicationContext

class Qc4Event {
    ApplicationContext applicationContext
    def redisService = applicationContext.getBean("redisService")
    def mqAction = applicationContext.getBean("mqAction")

    def condition(){
        def qc1Status = redisService.getSubTaskItem("qc1Status")
        def qc2Status = redisService.getSubTaskItem("qc2Status")
        def qc3Status = redisService.getSubTaskItem("qc3Status")

        return qc1Status && qc2Status && qc3Status;
    }

    def action(){
        def qc1Status = redisService.getSubTaskItem("qc1Status")
        def qc2Status = redisService.getSubTaskItem("qc2Status")
        def qc3Status = redisService.getSubTaskItem("qc3Status")
        mqAction.sendToQc4(qc1Status, qc2Status, qc3Status)
    }
}
