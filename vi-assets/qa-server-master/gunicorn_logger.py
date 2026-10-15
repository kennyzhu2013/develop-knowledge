import os
import time
import traceback

from gunicorn.glogging import Logger


class GLogger(Logger):
    def now(self):
        return time.strftime('%Y-%m-%d %H:%M:%S')

    def access(self, resp, req, environ, request_time):
        """ See http://httpd.apache.org/docs/2.0/logs.html#combined
        for format details
        """

        if not (self.cfg.accesslog or self.cfg.logconfig or
           self.cfg.logconfig_dict or self.cfg.logconfig_json or
           (self.cfg.syslog and not self.cfg.disable_redirect_access_to_syslog)):
            return

        # wrap atoms:
        # - make sure atoms will be test case insensitively
        # - if atom doesn't exist replace it by '-'
        safe_atoms = self.atoms_wrapper_class(
            self.atoms(resp, req, environ, request_time)
        )

        try:
            self.access_log.info(self.cfg.access_log_format, safe_atoms)
        except Exception:
            self.error(traceback.format_exc())

    def atoms(self, resp, req, environ, request_time):
        """ Gets atoms for log formatting.
        """
        status = resp.status
        if isinstance(status, str):
            status = status.split(None, 1)[0]
        remote_addr = environ.get('REMOTE_ADDR', '-')
        atoms = {
            'h': remote_addr,
            'l': '-',
            'u': self._get_user(environ) or '-',
            't': self.now(),
            'r': "%s %s %s" % (environ['REQUEST_METHOD'],
                               environ['RAW_URI'],
                               environ["SERVER_PROTOCOL"]),
            's': status,
            'm': environ.get('REQUEST_METHOD'),
            'U': environ.get('PATH_INFO'),
            'q': environ.get('QUERY_STRING'),
            'H': environ.get('SERVER_PROTOCOL'),
            'b': getattr(resp, 'sent', None) is not None and str(resp.sent) or '-',
            'B': getattr(resp, 'sent', None),
            'f': environ.get('HTTP_REFERER', '-'),
            'a': environ.get('HTTP_USER_AGENT', '-'),
            'T': request_time.seconds,
            'D': (request_time.seconds * 1000000) + request_time.microseconds,
            'M': (request_time.seconds * 1000) + int(request_time.microseconds / 1000),
            'L': "%d.%06d" % (request_time.seconds, request_time.microseconds),
            'p': "<%s>" % os.getpid()
        }

        # add request headers
        if hasattr(req, 'headers'):
            req_headers = req.headers
        else:
            req_headers = req

        if hasattr(req_headers, "items"):
            # h使用初始ip
            atoms.update({"h": req_headers.get("X-Forwarded-For", remote_addr).split(",")[0]})
            req_headers = req_headers.items()

        atoms.update({"{%s}i" % k.lower(): v for k, v in req_headers})

        resp_headers = resp.headers
        if hasattr(resp_headers, "items"):
            resp_headers = resp_headers.items()

        # add response headers
        atoms.update({"{%s}o" % k.lower(): v for k, v in resp_headers})

        # add environ variables
        environ_variables = environ.items()
        atoms.update({"{%s}e" % k.lower(): v for k, v in environ_variables})

        return atoms
