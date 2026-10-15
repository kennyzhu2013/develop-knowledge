from contextlib import contextmanager

from clickhouse_driver import Client
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker

from config import settings


class CkBase:
    """
    clickhouse初始化连接基类
    """
    default_engine = None
    engine_dict = None
    default_session_maker = None
    session_maker_dict = None

    @classmethod
    def load_ck(cls):
        # ck_url = "clickhouse://{USER}:{PASSWORD}@{HOST}:{PORT}/{DB}".format(**settings["CK_CONFIG"])
        # cls.default_engine = create_engine(ck_url, **settings["CK_ENGINE_CONFIG"])

        cls.default_engine = create_engine(settings['CK_DATABASE_URI'], **settings["CK_ENGINE_CONFIG"])
        cls.engine_dict = {k: create_engine(
            v, **settings["CK_ENGINE_CONFIG"]) for k, v in settings["CK_BINDS"].items()}
        cls.default_session_maker = sessionmaker(bind=cls.default_engine, expire_on_commit=False, autoflush=False)

        cls.session_maker_dict = {k: sessionmaker(bind=v, expire_on_commit=False) for k, v in cls.engine_dict.items()}


CkBase.load_ck()


@contextmanager
def ck_session_scope(engine=None):
    """clickhouse连接器"""
    session_maker = CkBase.session_maker_dict.get(engine, CkBase.default_session_maker)
    session = session_maker()
    try:
        yield session
    except:
        raise
    finally:
        session.close()


def ckSelectForSQL(sql, params=None, engine=None):
    if params is None:
        params = {}
    with ck_session_scope(engine) as ss:
        cursor = ss.execute(text(sql), params)
        fields = cursor._metadata.keys
        result_list = [dict(zip(fields, item)) for item in cursor.fetchall()]
    return result_list


def ckAddObjects(sql, data_list=None, engine=None):
    if not data_list:
        return 0
    key_dict = {"mid_strategy": "CLICKHOUSE_STRATEGY",
                "mid_bussops": "CLICKHOUSE_BUSSOPS",
                "mid_bussops_zyc": "CLICKHOUSE_BUSSOPS_ZYC",
                "ytx_db": "CLICKHOUSE_YTXDB",
                "bill_history": "CLICKHOUSE_BILL_HISTORY"}
    key = key_dict.get(engine, "CLICKHOUSE_BUSSOPS")
    # key = "CLICKHOUSE_STRATEGY" if engine == "mid_strategy" else "CLICKHOUSE_BUSSOPS"
    client = Client(**settings[key])
    count = client.execute(sql, data_list)
    return count
