import logging
import os

import paramiko

logger = logging.getLogger(__name__)


class SSHConnection(object):
    _transport = None
    _host = ""
    _port = 22
    _username = ""
    _pwd = ""

    def __init__(self, host, port=22, username="", pwd=""):
        self._host = host
        self._port = port
        self._username = username
        self._pwd = pwd
        self._k = None

    def connect(self):
        transport = paramiko.Transport((self._host, self._port))
        transport.connect(username=self._username, password=self._pwd)
        self._transport = transport

    def close(self):
        self._transport.close()

    def upload(self, local_path, target):
        """
        上传文件
        :param local_path: 本地文件路径 如 /data/test.txt
        :param target: 远程文件路径  如  remote/test.txt
        :return:
        """
        # 连接，上传
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        target_path = os.path.dirname(target)
        try:
            logger.info("{} {}".format(target_path, sftp.stat(target_path)))
        except FileNotFoundError as error:
            logger.info(error)
            sftp.mkdir(target_path)
            logger.info("mkdir {}".format(target_path))

        sftp.put(localpath=local_path, remotepath=target)

    def download(self, remote_path, local_path):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        sftp.get(remote_path, local_path)

    def cmd(self, command):
        ssh = paramiko.SSHClient()
        ssh._transport = self._transport
        # 执行命令
        stdin, stdout, stderr = ssh.exec_command(command)
        # 获取命令结果
        result = stdout.read()
        return str(result, encoding="utf-8")

    def rename(self, old_path, new_path):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        sftp.rename(old_path, new_path)
        logger.info("rename: {} {}".format(old_path, new_path))

    def mkdir(self, path):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        sftp.mkdir(path)

    def delete(self, file):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        sftp.remove(file)

    def listdir(self, path):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        files = sftp.listdir(path)
        return files

    def getsize(self, filepath):
        sftp = paramiko.SFTPClient.from_transport(self._transport)
        return sftp.stat(filepath).st_size
