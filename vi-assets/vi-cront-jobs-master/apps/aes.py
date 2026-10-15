from Crypto.Cipher import AES
from binascii import b2a_hex, a2b_hex


class AesUtil():
    def __init__(self, key, iv):
        self.key = key
        self.iv = iv
        self.mode = AES.MODE_CBC
        self.BS = AES.block_size
        # 补位
        self.pad = lambda s: s + (self.BS - len(s) % self.BS) * chr(self.BS - len(s) % self.BS)
        self.unpad = lambda s: s[0:-ord(s[-1])]

    def encrypt(self, text: str):
        text = self.pad(text).encode()
        cryptor = AES.new(self.key, self.mode, self.iv)
        # 目前AES-128 足够目前使用
        ciphertext = cryptor.encrypt(text)
        # 把加密后的字符串转化为16进制字符串
        return b2a_hex(ciphertext).decode()

    # 解密后，去掉补足的空格用strip() 去掉
    def decrypt(self, text):
        cryptor = AES.new(self.key, self.mode, self.iv)
        plain_text = cryptor.decrypt(a2b_hex(text))
        return self.unpad(plain_text.decode().rstrip("\0"))
