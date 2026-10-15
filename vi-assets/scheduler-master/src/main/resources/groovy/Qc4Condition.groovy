package groovy


def execute(Map<String, Object> params){
    def qc1Status = params.get("qc1Status");
    def qc2Status = params.get("qc2Status");
    def qc3Status = params.get("qc3Status");



    return qc1Status && qc2Status && qc3Status;
}

return this;

//def map = new HashMap();
//map.put("qc1Status", 1)
//map.put("qc2Status", 1)
//map.put("qc3Status", 1)
//
//boolean result = execute(map);
//println(result)

