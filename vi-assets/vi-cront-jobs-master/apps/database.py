import logging
from contextlib import contextmanager

from sqlalchemy import create_engine, text, Column, Integer, DateTime
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

from config import settings

logger = logging.getLogger(__name__)

DEFAULT_ENGINE = create_engine(
    settings["SQLALCHEMY_DATABASE_URI"], **settings["SQLALCHEMY_ENGINE_CONFIG"])

ENGINE_DICT = {k: create_engine(
    v, **settings["SQLALCHEMY_ENGINE_CONFIG"]) for k, v in settings["SQLALCHEMY_BINDS"].items()}

DEFAULT_SESSION_MAKER = sessionmaker(bind=DEFAULT_ENGINE, expire_on_commit=False)

SESSION_MAKER_DICT = {k: sessionmaker(bind=v, expire_on_commit=False) for k, v in ENGINE_DICT.items()}

# base
BASE_BUSSOPS = declarative_base(bind=DEFAULT_ENGINE)
BASE_SOUTH_BILL = declarative_base(bind=ENGINE_DICT.get("south_bill"))
BASE_NORTH_BILL = declarative_base(bind=ENGINE_DICT.get("north_bill"))
BASE_AUTH = declarative_base(bind=ENGINE_DICT.get("mid_auth"))
BASE_STRATEGY = declarative_base(bind=ENGINE_DICT.get("mid_strategy"))


@contextmanager
def session_scope(engine=None):
    """Provide a transactional scope around a series of operations."""
    session_maker = SESSION_MAKER_DICT.get(engine, DEFAULT_SESSION_MAKER)
    session = session_maker()
    try:
        yield session
        session.commit()
    except:
        session.rollback()
        raise
    finally:
        session.close()


def selectForSQL(sql, params=None, engine=None):
    result_list = []
    if params is None:
        params = {}
    with session_scope(engine) as ss:
        resultproxy = ss.execute(text(sql), params)
        for row in resultproxy:
            row_as_dict = dict(row)
            result_list.append(row_as_dict)
    return result_list


def executeForSQL(sql, params=None, engine=None):
    with session_scope(engine) as ss:
        ss.execute(text(sql), params)


class TableMixin(object):
    __table_args__ = {"mysql_engine": "InnoDB"}
    __mapper_args__ = {"always_refresh": True}

    id = Column(Integer, primary_key=True, autoincrement=True)
    create_time = Column(DateTime, server_default=text(
        "CURRENT_TIMESTAMP"), nullable=False)
    update_time = Column(DateTime, server_default=text(
        "CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"), nullable=False)


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
        ss.bulk_save_objects(obj_list)
    return True
