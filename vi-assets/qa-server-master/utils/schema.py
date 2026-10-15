import logging
from urllib.parse import urlparse

from jsonschema import validate
from jsonschema.exceptions import SchemaError, ValidationError


logger = logging.getLogger("all")


def validate_json(data, schema):
    try:
        validate(instance=data, schema=schema)
    except SchemaError as e:
        error_info = "wrong schema：{}-{}".format([i for i in e.path], e.message)
        logger.info(error_info)
        return error_info
    except ValidationError as e:
        error_info = "wrong field:{}-{}".format([i for i in e.path], e.message)
        logger.info(error_info)
        return error_info
    return ""


def is_valid_url(url):
    try:
        result = urlparse(url)
        # print(result.netloc)
        return all([result.scheme, result.netloc])
    except ValueError:
        return False

