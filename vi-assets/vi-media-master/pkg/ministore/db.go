package ministore

import (
	"os"
	"path/filepath"
	"sync"
)

type SimpleDB struct {
	sync.RWMutex
	// 内存中的索引信息, 对应键值对为key-offset
	indexes map[string]int64
	// 数据文件
	dbFile *IdxDBFile
	// 数据目录
	dirPath string

	// 重写时启用
	rwIndexes map[string]int64
	rwDbFile  *IdxDBFile
}

// New 创建一个指定的数据库实例
func New(dirPath, filename string) (*SimpleDB, error) {
	// 如果数据库目录不存在，则新建一个
	if _, err := os.Stat(dirPath); err != nil && os.IsNotExist(err) {
		if err = os.MkdirAll(dirPath, os.ModePerm); err != nil {
			return nil, Wrap(ErrDirCreate, err.Error())
		}
	}
	// 加载数据文件
	dbFile, err := NewDBFileWithFullName(filepath.Join(dirPath, filename), os.O_RDWR|os.O_CREATE|os.O_TRUNC)
	if err != nil {
		return nil, err
	}
	return &SimpleDB{indexes: make(map[string]int64), dbFile: dbFile, dirPath: dirPath}, nil
}

// Open 在dirPath目录下开启一个数据库实例.
// 增加所有记录的返回list.
func Open(dirPath string) (*SimpleDB, []*Entity, error) {
	// 如果数据库目录不存在，则新建一个
	if _, err := os.Stat(dirPath); err != nil && os.IsNotExist(err) {
		if err = os.MkdirAll(dirPath, os.ModePerm); err != nil {
			return nil, nil, Wrap(ErrDirCreate, err.Error())
		}
	}
	// 加载数据文件
	dbFile, err := NewDBFile(dirPath)
	if err != nil {
		return nil, nil, err
	}
	// 加载索引
	db := &SimpleDB{indexes: make(map[string]int64), dbFile: dbFile, dirPath: dirPath}
	eList, lErr := db.loadIndexesFromFile()
	return db, eList, lErr
}

// 从文件当中加载索引，返回全局结构的list
func (db *SimpleDB) loadIndexesFromFile() ([]*Entity, error) {
	if db.dbFile == nil || db.dbFile.Offset == 0 {
		return nil, nil
	}

	var offset int64
	var eList []*Entity
	eSet := map[string]*Entity{}
	for {
		entity, err := db.dbFile.Read(offset)
		if err != nil {
			// 读取完毕
			if IsIndexEOF(err) {
				break
			}
			return nil, Wrap(ErrIndexDamage, err.Error())
		}
		key := bytes2String(entity.Key)
		if entity.Mark == DEL {
			// 删除无效的key
			delete(db.indexes, key)
			delete(eSet, key)
		} else {
			eSet[key] = entity
			db.indexes[key] = offset
		}
		offset += entity.GetSize()
	}
	for _, e := range eSet {
		eList = append(eList, e)
	}
	return eList, nil
}

func (db *SimpleDB) Put(key, value []byte) error {
	if len(key) == 0 {
		return nil
	}
	db.Lock()
	defer db.Unlock()

	offset := db.dbFile.Offset
	// 封装成Entry
	entry := NewEntity(key, value, PUT)
	// 追加到数据文件当中
	if err := db.dbFile.Write(entry); err != nil {
		return err
	}
	// 写到内存
	db.indexes[bytes2String(key)] = offset
	return nil
}

func (db *SimpleDB) PutUnsafe(key, value []byte) error {
	if len(key) == 0 {
		return nil
	}
	offset := db.dbFile.Offset
	// 封装成Entry
	entry := NewEntity(key, value, PUT)
	// 追加到数据文件当中
	if err := db.dbFile.Write(entry); err != nil {
		return err
	}
	// 写到内存
	db.indexes[bytes2String(key)] = offset
	return nil
}

func (db *SimpleDB) Get(key []byte) ([]byte, error) {
	if len(key) == 0 {
		return nil, nil
	}
	db.RLock()
	defer db.RUnlock()

	// 从内存当中取出索引信息
	offset, ok := db.indexes[bytes2String(key)]
	// key不存在
	if !ok {
		return nil, Wrap(ErrIndexNotExists, "index is not exists")
	}

	// 从磁盘中读取数据
	e, err := db.dbFile.Read(offset)
	if err != nil {
		return nil, err
	}
	return e.Value, nil
}

func (db *SimpleDB) Del(key []byte) error {
	if len(key) == 0 {
		return nil
	}
	db.Lock()
	defer db.Unlock()

	// 从内存当中取出索引信息
	_, ok := db.indexes[bytes2String(key)]
	// key不存在，忽略
	if !ok {
		return nil
	}

	// 封装成Entry并写入
	entry := NewEntity(key, nil, DEL)
	if err := db.dbFile.Write(entry); err != nil {
		return err
	}
	// 删除内存中的key
	delete(db.indexes, bytes2String(key))
	return nil
}

func (db *SimpleDB) EnableRewrite() error {
	if db.rwDbFile != nil {
		return nil
	}
	db.Lock()
	defer db.Unlock()
	if db.rwDbFile == nil {
		rwDbFile, err := NewMergeDBFile(db.dirPath)
		if err != nil {
			return err
		}
		db.rwIndexes = make(map[string]int64)
		db.rwDbFile = rwDbFile
	}
	return nil
}

func (db *SimpleDB) Rewrite(key, value []byte, mark uint16) error {
	offset := db.rwDbFile.Offset
	// 追加到新索引文件
	err := db.rwDbFile.Write(NewEntity(key, value, mark))
	if err != nil {
		return err
	}
	// 写到新索引内存
	db.rwIndexes[bytes2String(key)] = offset
	return nil
}

func (db *SimpleDB) DisableRewrite(completed bool) error {
	if db.rwDbFile == nil {
		return nil
	}
	db.Lock()
	defer db.Unlock()
	if db.rwDbFile != nil {
		if !completed {
			// Rewrite failed
			db.truncateRewrite()
			return nil
		}
		// Replace the old index file
		name := db.dbFile.Name()
		err := os.Rename(db.rwDbFile.Name(), db.dbFile.Name())
		if err != nil {
			db.truncateRewrite()
			return Wrap(ErrIndexRename, err.Error())
		}
		err = db.dbFile.Open(name)
		if err != nil {
			db.rwIndexes = nil
			db.rwDbFile = nil
			return Wrap(ErrIndexRename, err.Error())
		}
		db.indexes = db.rwIndexes
		db.rwIndexes = nil
		db.rwDbFile = nil
	}
	return nil
}

func (db *SimpleDB) truncateRewrite() {
	// 清除新索引内存
	db.rwIndexes = nil
	// 清除新索引文件
	_ = db.rwDbFile.Close()
	_ = os.Remove(db.rwDbFile.Name())
	db.rwDbFile = nil
}

func (db *SimpleDB) Truncate() {
	if db.dbFile == nil {
		return
	}
	db.indexes = nil
	_ = db.dbFile.Close()
	_ = os.Remove(db.dbFile.Name())
	db.dbFile = nil
}

func (db *SimpleDB) Open(filename string) error {
	if db.dbFile == nil {
		dbFile, err := NewDBFileWithFullName(filename, os.O_RDWR|os.O_CREATE)
		if err != nil {
			return err
		}
		db.Lock()
		db.dbFile = dbFile
		db.Unlock()
		return nil
	}
	db.Lock()
	defer db.Unlock()
	return db.dbFile.Open(filename)
}

func (db *SimpleDB) Close() error {
	if db.dbFile == nil {
		return nil
	}
	return db.dbFile.Close()
}

func (db *SimpleDB) IsExist(key []byte) bool {
	if len(key) == 0 {
		return false
	}
	db.RLock()
	defer db.RUnlock()
	_, ok := db.indexes[bytes2String(key)]
	return ok
}

func (db *SimpleDB) DBName() string {
	if db.dbFile == nil {
		return ""
	}
	return db.dbFile.Name()
}

func bytes2String(b []byte) string {
	return string(b)
}