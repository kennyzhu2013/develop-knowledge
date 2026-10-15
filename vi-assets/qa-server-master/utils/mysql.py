from contextlib import contextmanager

from sqlalchemy import Column, DateTime, Integer, create_engine, text, delete, update
from sqlalchemy.orm import sessionmaker, DeclarativeBase

from config import settings


DEFAULT_MYSQL_ENGINE = create_engine(settings["MYSQL_URI"], **settings["MYSQL_ENGINE_CONFIG"])
MYSQL_ENGINE_DICT = {k: create_engine(v, **settings["MYSQL_ENGINE_CONFIG"]) for k, v in settings["MYSQL_BINDS"].items()}
DEFAULT_MYSQL_SESSION_MAKER = sessionmaker(bind=DEFAULT_MYSQL_ENGINE, expire_on_commit=False, autoflush=False)
MYSQL_SESSION_MAKER_DICT = {k: sessionmaker(bind=v, expire_on_commit=False, autoflush=False) for k, v in MYSQL_ENGINE_DICT.items()}


@contextmanager
def session_scope(engine=None):
    """Provide a transactional scope around a series of operations."""
    session_maker = MYSQL_SESSION_MAKER_DICT.get(engine, DEFAULT_MYSQL_SESSION_MAKER)
    session = session_maker()
    try:
        yield session
        session.commit()
    except:
        session.rollback()
        raise
    finally:
        session.close()


def select_in_mysql(sql, params=None, engine=None):
    result_list = []
    if params is None:
        params = {}
    with session_scope(engine) as ss:
        result_proxy = ss.execute(text(sql), params)
        for row in result_proxy:
            row_as_dict = dict(row._mapping)
            result_list.append(row_as_dict)
    return result_list


# base
class MysqlBase(DeclarativeBase):
    pass


class TableMixin(object):
    __table_args__ = {"mysql_engine": "InnoDB"}
    __mapper_args__ = {"always_refresh": True}

    id = Column(Integer, primary_key=True, autoincrement=True)
    create_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP"), nullable=False)
    update_time = Column(DateTime, server_default=text("CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"), nullable=False)


def add_object(obj, engine=None):
    """
    添加
    :param engine:
    :param obj:
    :return:
    """
    with session_scope(engine) as ss:
        ss.add(obj)
        ss.flush()
        return obj.id


def add_obj_list(obj_list, engine=None):
    """
    批量添加
    :param engine:
    :param obj_list:
    :return:
    """
    with session_scope(engine) as ss:
        ss.add_all(obj_list)
    return True


def delete_obj_by_id(object_class, obj_id, engine=None):
    """
    根据id删除
    :param object_class:
    :param obj_id:
    :param engine:
    :return:
    """
    with session_scope(engine) as ss:
        ss.execute(delete(object_class).where(object_class.id == obj_id))


def update_obj_by_id(object_class, obj_id, properties, engine=None):
    """
    根据id更新
    :param properties:
    :param object_class:
    :param obj_id:
    :param engine:
    :return:
    """
    with session_scope(engine) as ss:
        ss.execute(update(object_class).where(object_class.id == obj_id).values(properties))


def get_obj_by_id(object_class, obj_id, engine=None):
    """
    根据id查对象
    :param object_class:
    :param obj_id:
    :param engine:
    :return:
    """
    with session_scope(engine) as ss:
        return ss.get(object_class, obj_id)
