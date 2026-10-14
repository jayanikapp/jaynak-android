import os, sys
SRC = os.environ.get("SRC", "/app/otp_server.py")
DST = os.environ.get("DST", "/tmp/app/otp_server.py")
t = open(SRC, encoding="utf-8").read()
if "/auth/firebase" in t:
    sys.exit(0)

FB = '''
FIREBASE_API_KEY = os.environ.get("FIREBASE_API_KEY", "AIzaSyAS1oUQVnpyl7_tBZglFrlvwH56hYFV-Hc").strip()


def firebase_phone_from_token(id_token):
    if not id_token or not FIREBASE_API_KEY:
        return None
    url = ("https://identitytoolkit.googleapis.com/v1/accounts:lookup?key="
           + urllib.parse.quote(FIREBASE_API_KEY))
    req = urllib.request.Request(
        url, data=json.dumps({"idToken": id_token}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            users = json.loads(r.read().decode()).get("users") or []
    except Exception as e:
        print("Firebase lookup failed: %s" % e, flush=True)
        return None
    if not users:
        return None
    return normalize(users[0].get("phoneNumber", ""))


'''
HANDLER = '''    def firebase_login(self):
        body = self._body()
        phone = firebase_phone_from_token(str(body.get("id_token", "")))
        if not phone:
            return self._json(401, {"error": "invalid_token"})
        self._json(200, {"ok": True, "phone": phone, "token": issue_session(phone)})

'''

def rep(a, b):
    global t
    assert t.count(a) == 1, a[:40]
    t = t.replace(a, b)

rep('\nTW_SID = os.environ.get("TWILIO_ACCOUNT_SID"', FB + 'TW_SID = os.environ.get("TWILIO_ACCOUNT_SID"')
rep('        elif self.path == "/auth/verify-otp":\n            self.verify_otp()\n',
    '        elif self.path == "/auth/verify-otp":\n            self.verify_otp()\n        elif self.path == "/auth/firebase":\n            self.firebase_login()\n')
rep('    def verify_otp(self):\n', HANDLER + '    def verify_otp(self):\n')
os.makedirs(os.path.dirname(DST), exist_ok=True)
open(DST, "w", encoding="utf-8").write(t)
