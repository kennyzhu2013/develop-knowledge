package metric

type MetricConfig struct {
	Namespace string
	Id        string
	Version   string
	Labels    map[string]string //extra kv for all collector and func

}

type GaugeFunctionsType map[string]func() float64
