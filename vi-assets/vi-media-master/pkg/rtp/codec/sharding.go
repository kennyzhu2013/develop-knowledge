package codec

const (
	MtuG729 = 20
	MtuPCMA = 160
	MtuPCMU = 160
	MtuH264 = 1200
)

type Fragment struct {
	Frames   [][]byte
	FType    int
	KeyFrame bool
}

func Sharding(mimeType string, p []byte) []*Fragment {
	switch mimeType {
	case MimeTypeAmrNB:
		return []*Fragment{(&AmrNbSharding{}).Sharding(p)}
	case MimeTypeAmrNBOctet:
		return []*Fragment{(&AmrNbOctetSharding{}).Sharding(p)}
	case MimeTypeAmrWB:
		return []*Fragment{(&AmrWbSharding{}).Sharding(p)}
	case MimeTypeAmrWBOctet:
		return []*Fragment{(&AmrWbOctetSharding{}).Sharding(p)}
	case MimeTypePCMA:
		return []*Fragment{(&G711Sharding{}).Sharding(MtuPCMA, p)}
	case MimeTypePCMU:
		return []*Fragment{(&G711Sharding{}).Sharding(MtuPCMU, p)}
	case MimeTypeG729:
		return []*Fragment{(&G729Sharding{}).Sharding(MtuG729, p)}
	case MimeTypeLowH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeMidH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeHigH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeExtH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	}
	return nil
}

func ShardingFile(mimeType string, p []byte) []*Fragment {
	switch mimeType {
	case MimeTypeAmrNB:
		return []*Fragment{(&AmrNbSharding{}).ShardingFile(p)}
	case MimeTypeAmrNBOctet:
		return []*Fragment{(&AmrNbOctetSharding{}).ShardingFile(p)}
	case MimeTypeAmrWB:
		return []*Fragment{(&AmrWbSharding{}).ShardingFile(p)}
	case MimeTypeAmrWBOctet:
		return []*Fragment{(&AmrWbOctetSharding{}).ShardingFile(p)}
	case MimeTypePCMA:
		return []*Fragment{(&G711Sharding{}).ShardingFile(MtuPCMA, p)}
	case MimeTypePCMU:
		return []*Fragment{(&G711Sharding{}).ShardingFile(MtuPCMU, p)}
	case MimeTypeG729:
		return []*Fragment{(&G729Sharding{}).ShardingFile(MtuG729, p)}
	case MimeTypeLowH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeMidH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeHigH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	case MimeTypeExtH264:
		return (&H264Sharding{}).Sharding(MtuH264, p)
	}
	return nil
}