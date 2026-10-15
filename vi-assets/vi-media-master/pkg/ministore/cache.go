package ministore

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

const DefaultExpiredScanInterval = 10 * time.Second
const DefaultExpiredFetchSize = 2000
const DefaultFetchSize = 10000

const DefaultLifeSpan = 10 * time.Minute
const DefaultTableSize = 1024

type CacheTable struct {
	sync.RWMutex

	// 级别
	level uint8
	// 缓存区域
	zone string
	// 缓存表元素默认生命周期
	// 如果缓存表元素本身设置了生命周期，则以元素本身设置优先
	lifeSpan time.Duration

	// IO具体实现
	IoStream
	// 负载均衡策略
	LBPolicy

	// 索引数据库
	idxDB *SimpleDB
	// 缓存列表
	items map[string]*CacheItem
	// 缓存元素数量
	size int

	// 重写时启用
	// 重写缓冲区，保存重写期间缓存的修改操作，value值为nil代表删除
	rewriteBuffer map[string]*CacheItem

	// 数据操作处理，非耗时操作
	dataOp func(*CacheTable, string, []byte)
	// 过期处理
	expireOp func(*CacheTable, string)
	// 加载预处理
	loadPreOp func(*CacheItem)
}

func NewCacheTable(zone, idxDir string, opts ...Option) (*CacheTable, error) {
	var err error
	var idxDB *SimpleDB
	var idxData []*Entity
	if idxDB, idxData, err = Open(idxDir); err != nil {
		return nil, err
	}

	table := &CacheTable{
		zone:     zone,
		idxDB:    idxDB,
		lifeSpan: DefaultLifeSpan,
	}
	for _, opt := range opts {
		opt.apply(table)
	}
	if table.items == nil {
		table.items = make(map[string]*CacheItem, DefaultTableSize)
	}
	if table.IoStream == nil {
		table.IoStream = &DiskIoStream{}
	}

	// 根据索引数据快速建立缓存表
	for _, e := range idxData {
		name := Bytes2String(e.Key)
		if e.Mark == DEL {
			delete(table.items, name)
			continue
		}
		item := &CacheItem{}
		_ = json.Unmarshal(e.Value, item)
		table.items[name] = item
		// 数据加载预处理
		if table.loadPreOp != nil {
			table.loadPreOp(item)
		}
	}
	table.size = len(table.items)
	if table.size == 0 {
		// 缓存表为空
		// 可能第一次创建索引，或者索引数据为空，需尝试从IO中恢复
		err = Wrap(ErrIndexEmpty, "the index is empty")
	}
	return table, err
}

func (x *CacheTable) Add(name, path, volume, metadata string) (*CacheItem, error) {
	x.Lock()
	item, ok := x.items[name]
	if !ok {
		// 并发添加同一缓存key时，先拿到锁的协程先在缓存中标记key
		// 防止同一时间另一协程进来写入，导致数据混乱
		x.items[name] = nil
	}
	x.Unlock()
	if item != nil {
		return item, Wrap(ErrExists, fmt.Sprintf("the %v already exists", name))
	}
	if ok {
		return nil, Wrap(ErrConcurrentModification, fmt.Sprintf("the %v Writes in progress", name))
	}
	// 写入索引并添加到缓存
	nowTime := time.Now()
	item = &CacheItem{Name: name, Path: path, Volume: volume, Metadata: metadata, CreatedOn: nowTime, AccessedOn: nowTime}
	if err := x.writeIndex(item); err != nil {
		// 清除写入标记
		x.clearWrittenMark(name)
		return nil, err
	}
	x.Lock()
	x.items[name] = item
	if x.rewriteBuffer != nil {
		x.rewriteBuffer[name] = item
	}
	x.size++
	x.Unlock()
	return item, nil
}

func (x *CacheTable) AddAndCopy(name, path, volume, metadata string) (*CacheItem, error) {
	x.Lock()
	item, ok := x.items[name]
	if !ok {
		x.items[name] = nil
	}
	x.Unlock()
	if item != nil {
		return item, Wrap(ErrExists, fmt.Sprintf("the %v already exists", name))
	}
	if ok {
		return nil, Wrap(ErrConcurrentModification, fmt.Sprintf("the %v Writes in progress", name))
	}
	if x.LBPolicy == nil {
		x.clearWrittenMark(name)
		return nil, Wrap(ErrInvalidLBPolicy, "unable to determine the dstVolume, policy is not found")
	}
	// 数据拷贝
	dstVolume := x.Choose()
	err := x.Copy(filepath.Join(dstVolume, path, name), filepath.Join(volume, path, name))
	if err != nil {
		x.clearWrittenMark(name)
		return nil, err
	}
	if dstVolume == "" {
		x.clearWrittenMark(name)
		return nil, Wrap(ErrInvalidLBPolicy, "unable to determine the dstVolume, volumes is empty")
	}
	// 写入索引并添加到缓存
	nowTime := time.Now()
	item = &CacheItem{Name: name, Path: path, Volume: dstVolume, Metadata: metadata, CreatedOn: nowTime, AccessedOn: nowTime}
	if err = x.writeIndex(item); err != nil {
		// 删除数据
		_ = x.Remove(filepath.Join(item.Volume, item.Path, item.Name))
		// 清除写入标记
		x.clearWrittenMark(name)
		return nil, err
	}
	x.Lock()
	x.items[name] = item
	if x.rewriteBuffer != nil {
		x.rewriteBuffer[name] = item
	}
	x.size++
	x.Unlock()
	return item, nil
}

func (x *CacheTable) AddWithData(name, path, metadata string, data []byte) (*CacheItem, error) {
	x.Lock()
	item, ok := x.items[name]
	if !ok {
		x.items[name] = nil
	}
	x.Unlock()
	if item != nil {
		return item, Wrap(ErrExists, fmt.Sprintf("the %v already exists", name))
	}
	if ok {
		return nil, Wrap(ErrConcurrentModification, fmt.Sprintf("the %v Writes in progress", name))
	}
	if x.LBPolicy == nil {
		x.clearWrittenMark(name)
		return nil, Wrap(ErrInvalidLBPolicy, "unable to determine the dstVolume, policy is not found")
	}
	// 数据写入
	dstVolume := x.Choose()
	err := x.Write(data, filepath.Join(dstVolume, path, name))
	if err != nil {
		x.clearWrittenMark(name)
		return nil, err
	}
	if dstVolume == "" {
		x.clearWrittenMark(name)
		return nil, Wrap(ErrInvalidLBPolicy, "unable to determine the dstVolume, volumes is empty")
	}
	// 写入索引并添加到缓存
	nowTime := time.Now()
	item = &CacheItem{Name: name, Path: path, Volume: dstVolume, Metadata: metadata, CreatedOn: nowTime, AccessedOn: nowTime}
	if err = x.writeIndex(item); err != nil {
		// 删除数据
		_ = x.Remove(filepath.Join(item.Volume, item.Path, item.Name))
		// 清除写入标记
		x.clearWrittenMark(name)
		return nil, err
	}
	x.Lock()
	x.items[name] = item
	if x.rewriteBuffer != nil {
		x.rewriteBuffer[name] = item
	}
	x.size++
	x.Unlock()
	return item, nil
}

func (x *CacheTable) Delete(name string) (*CacheItem, error) {
	x.RLock()
	item := x.items[name]
	x.RUnlock()
	if item == nil {
		return nil, nil
	}
	// 先删索引再删缓存
	// 这里忽略索引删除失败
	err := x.removeIndex(item)
	x.Lock()
	delete(x.items, name)
	if x.rewriteBuffer != nil {
		x.rewriteBuffer[name] = nil
	}
	x.size--
	x.Unlock()
	// 删除数据
	_ = x.Remove(filepath.Join(item.Volume, item.Path, item.Name))
	return item, err
}

func (x *CacheTable) Get(name string) *CacheItem {
	x.RLock()
	defer x.RUnlock()
	return x.items[name]
}

func (x *CacheTable) GetData(name string) ([]byte, error) {
	x.RLock()
	item := x.items[name]
	x.RUnlock()
	if item == nil {
		return nil, Wrap(ErrNotExists, fmt.Sprintf("the %v is not exists", name))
	}
	// 获取数据
	// 如果不存在，直接删除
	data, err := x.Read(filepath.Join(item.Volume, item.Path, item.Name))
	if err != nil {
		if !IsFileNotExists(err) {
			return nil, err
		}
		_ = x.removeIndex(item)
		x.Lock()
		delete(x.items, name)
		if x.rewriteBuffer != nil {
			x.rewriteBuffer[name] = nil
		}
		x.size--
		x.Unlock()
		return nil, err
	}
	// 数据过期处理（惰性处理）
	if x.expireOp != nil {
		lifeSpan := item.LifeSpan
		if lifeSpan == 0 {
			lifeSpan = x.lifeSpan
		}
		if time.Now().Sub(item.AccessedOn) >= lifeSpan {
			x.expireOp(x, name)
			return data, nil
		}
	}
	// 数据操作处理
	if x.dataOp != nil {
		x.dataOp(x, name, data)
	}
	return data, nil
}

func (x *CacheTable) writeIndex(item *CacheItem) error {
	key := String2Bytes(item.Name)
	val, _ := json.Marshal(item)
	return x.idxDB.Put(key, val)
}

func (x *CacheTable) removeIndex(item *CacheItem) error {
	return x.idxDB.Del(String2Bytes(item.Name))
}

func (x *CacheTable) Rewrite() error {
	defer func() {
		if v := recover(); v != nil {

		}
	}()
	// 缓存信息拷贝，初始化重写缓冲区
	oldItems := make(map[string]*CacheItem, len(x.items))
	x.Lock()
	for k, v := range x.items {
		oldItems[k] = v
	}
	x.rewriteBuffer = make(map[string]*CacheItem)
	x.Unlock()

	// 打开索引数据重写
	err := x.idxDB.EnableRewrite()
	if err != nil {
		return err
	}
	// 索引写入
	for k, v := range oldItems {
		key := String2Bytes(k)
		val, _ := json.Marshal(v)
		err = x.idxDB.Rewrite(key, val, PUT)
		if err != nil {
			// 索引写入失败，结束重写
			_ = x.idxDB.DisableRewrite(false)
			// 清空重写内存区域
			x.Lock()
			x.rewriteBuffer = nil
			x.Unlock()
			return err
		}
	}

	x.Lock()
	defer x.Unlock()
	// 合并重写缓冲区
	for k, v := range x.rewriteBuffer {
		key := String2Bytes(k)
		if v == nil {
			err = x.idxDB.Rewrite(key, nil, DEL)
		} else {
			val, _ := json.Marshal(v)
			err = x.idxDB.Rewrite(key, val, PUT)
		}
		if err != nil {
			// 索引写入失败，结束重写
			break
		}
	}
	// 结束索引数据重写
	dErr := x.idxDB.DisableRewrite(err == nil)
	x.rewriteBuffer = nil
	if err != nil {
		return err
	}
	return dErr
}

// Rebuild 重建缓存表和索引，耗时操作
// 协程不安全，必须在无流量情况下执行，如果涉及到到缓冲区IO移动，则必须先停止
func (x *CacheTable) Rebuild(idxDir string, volumes ...string) error {
	if len(volumes) == 0 {
		return nil
	}
	defer func() {
		if v := recover(); v != nil {
		}
	}()

	idxDB, err := New(idxDir, RebuildFileName)
	if err != nil {
		return err
	}
	cacheItems := x.items
	if len(cacheItems) != 0 {
		cacheItems = make(map[string]*CacheItem)
	}
	// 扫描，耗时操作
	var fileTree *TreeInfos
	for _, volume := range volumes {
		err = x.MkDir(volume)
		if err != nil {
			return err
		}
		fileTree, err = x.Walk(volume)
		if err != nil {
			return err
		}
		// 建立缓存表
		for _, item := range fileTree.Files {
			if item.FType == IsDirectory || item.FType == IsSymlink {
				continue
			}
			// 缓存写入
			items := strings.Split(item.FName, string(filepath.Separator))
			fName := items[len(items)-1]
			fPath := item.FName[len(volume) : len(item.FName)-len(fName)-1]
			cacheItems[fName] = &CacheItem{Name: fName, Path: fPath, Volume: volume, CreatedOn: item.FMtime, AccessedOn: item.FMtime}
			// 索引写入
			key := String2Bytes(fName)
			val, _ := json.Marshal(cacheItems[fName])
			err = idxDB.PutUnsafe(key, val)
			if err != nil {
				// 索引写入失败，清除数据库文件
				idxDB.Truncate()
				return err
			}
		}
	}
	// 替换旧索引文件
	name := x.idxDB.DBName()
	err = os.Rename(idxDB.DBName(), x.idxDB.DBName())
	if err != nil {
		return Wrap(ErrIndexRename, err.Error())
	}
	// 重新打开索引文件
	err = idxDB.Open(name)
	if err != nil {
		return Wrap(ErrIndexRename, err.Error())
	}
	x.items = cacheItems
	x.idxDB = idxDB
	x.size = len(cacheItems)
	return nil
}

var FetchSize = DefaultFetchSize
var ExpiredFetchSize = DefaultExpiredFetchSize

func (x *CacheTable) ExpiredScan(ctx context.Context, sign chan struct{}, interval time.Duration) {
	defer func() {
		if v := recover(); v != nil {
		}
	}()

	timer := time.NewTimer(interval)
	for {
		select {
		case <-ctx.Done():
			return
		case <-sign:
			x.doExpiredScan()
			if !timer.Stop() {
				select {
				case <-timer.C:
				default:
				}
			}
			timer.Reset(interval)
		case <-timer.C:
			x.doExpiredScan()
			timer.Reset(interval)
		}
	}
}

func (x *CacheTable) doExpiredScan() {
	// 加锁过程中，为了防止缓存表过大导致过期扫描慢，引发阻塞问题
	// 采用定期随机抽取策略减少加锁时间
	total := 0
	targetCnt := 0
	nowTime := time.Now()
	var expiredKeys []string
	x.RLock()
	for name, item := range x.items {
		if item == nil {
			continue
		}
		lifeSpan := item.LifeSpan
		if lifeSpan == 0 {
			lifeSpan = x.lifeSpan
		}
		span := nowTime.Sub(item.AccessedOn)
		if span >= lifeSpan {
			expiredKeys = append(expiredKeys, name)
			targetCnt++
			if targetCnt >= ExpiredFetchSize {
				break
			}
		}
		total++
		if total >= FetchSize {
			break
		}
	}
	x.RUnlock()

	// 过期处理
	if x.expireOp != nil {
		for _, name := range expiredKeys {
			x.expireOp(x, name)
		}
	} else {
		for _, name := range expiredKeys {
			_, _ = x.Delete(name)
		}
	}
}

func (x *CacheTable) SetLifeSpan(lifeSpan time.Duration) {
	x.Lock()
	x.lifeSpan = lifeSpan
	x.Unlock()
}

func (x *CacheTable) SetDataOp(dataOp func(*CacheTable, string, []byte)) {
	x.Lock()
	x.dataOp = dataOp
	x.Unlock()
}

func (x *CacheTable) SetExpireOp(expireOp func(*CacheTable, string)) {
	x.Lock()
	x.expireOp = expireOp
	x.Unlock()
}

func (x *CacheTable) SetLevel(level uint8) {
	x.level = level
}

func (x *CacheTable) Level() uint8 {
	return x.level
}

func (x *CacheTable) Zone() string {
	return x.zone
}

func (x *CacheTable) Size() int {
	return x.size
}

func (x *CacheTable) clearWrittenMark(name string) {
	x.Lock()
	delete(x.items, name)
	x.Unlock()
}