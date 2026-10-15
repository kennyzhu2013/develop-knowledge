package record

type Options struct {
	Id         string
	GId        string
	Volume     string
	TimeStamp  int64
	InMemory   bool
	NonDivided bool
}

type Option func(*Options)

func Id(id string) Option {
	return func(o *Options) {
		o.Id = id
	}
}

func GId(groupId string) Option {
	return func(o *Options) {
		o.GId = groupId
	}
}

func Volume(volume string) Option {
	return func(o *Options) {
		o.Volume = volume
	}
}

func TimeStamp(timestamp int64) Option {
	return func(o *Options) {
		o.TimeStamp = timestamp
	}
}

func InMemory(inMemory bool) Option {
	return func(o *Options) {
		o.InMemory = inMemory
	}
}

func NonDivided(nonDivided bool) Option {
	return func(o *Options) {
		o.NonDivided = nonDivided
	}
}