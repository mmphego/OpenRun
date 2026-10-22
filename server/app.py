"""Optional OpenRun account service. Run behind HTTPS; backups are encrypted by the Android client."""
from __future__ import annotations
import base64, hashlib, hmac, json, os, secrets, sqlite3, threading, time
from collections import defaultdict, deque
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from cryptography.fernet import Fernet
from race_services import RaceServices,RaceServiceError

os.umask(0o077)
MAX_BACKUP=50*1024*1024
class ApiError(Exception):
    def __init__(self,status,message): self.status,self.message=status,message

class Service:
    def __init__(self,path):
        self.races=RaceServices();self.path=path;self.lock=threading.RLock();self.requests=defaultdict(deque)
        with self.db() as db:
            db.executescript('''CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE,salt TEXT,password TEXT);
CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,user TEXT,expires INTEGER);
CREATE TABLE IF NOT EXISTS backups(user TEXT PRIMARY KEY,revision INTEGER,payload BLOB,updated INTEGER);
CREATE TABLE IF NOT EXISTS oauth(state TEXT PRIMARY KEY,user TEXT,expires INTEGER);
CREATE TABLE IF NOT EXISTS strava(user TEXT PRIMARY KEY,payload BLOB);''')
    def db(self):
        db=sqlite3.connect(self.path,timeout=15);db.execute('PRAGMA foreign_keys=ON');return db
    @staticmethod
    def digest(password,salt):return hashlib.pbkdf2_hmac('sha256',password.encode(),bytes.fromhex(salt),600000).hex()
    def authenticate(self,authorization):
        if not authorization.startswith('Bearer '):raise ApiError(401,'Sign in first')
        hashed=hashlib.sha256(authorization[7:].encode()).hexdigest()
        with self.db() as db:row=db.execute('SELECT user FROM sessions WHERE token=? AND expires>?',(hashed,time.time())).fetchone()
        if not row:raise ApiError(401,'Sign in again')
        return row[0]
    def account(self,path,data,ip):
        if not isinstance(data,dict):raise ApiError(400,"Expected an account object")
        now=time.monotonic()
        with self.lock:
            attempts=self.requests[ip]
            while attempts and now-attempts[0]>60:attempts.popleft()
            if len(attempts)>=10:raise ApiError(429,'Too many attempts; wait a minute')
            attempts.append(now)
        email=data.get('email','');password=data.get('password','')
        if not isinstance(email,str) or not isinstance(password,str):raise ApiError(400,'Email and password must be text')
        email=email.strip().lower()
        if not isinstance(email,str) or not isinstance(password,str) or '@' not in email or len(email)>254 or not 12<=len(password)<=256:raise ApiError(400,'Use an email and a password of 12–256 characters')
        with self.db() as db:
            if path=='/v1/register':
                user=secrets.token_hex(16);salt=secrets.token_hex(16)
                try:db.execute('INSERT INTO users VALUES(?,?,?,?)',(user,email,salt,self.digest(password,salt)))
                except sqlite3.IntegrityError:raise ApiError(409,'Account already exists')
            else:
                row=db.execute('SELECT id,salt,password FROM users WHERE email=?',(email,)).fetchone()
                salt=row[1] if row else '00'*16;candidate=self.digest(password,salt)
                if not row or not hmac.compare_digest(candidate,row[2]):raise ApiError(401,'Email or password incorrect')
                user=row[0]
            token=secrets.token_urlsafe(32);db.execute('INSERT INTO sessions VALUES(?,?,?)',(hashlib.sha256(token.encode()).hexdigest(),user,int(time.time())+30*86400))
        return {'token':token,'expires_in':30*86400}
    def strava_ready(self):return bool(os.getenv('STRAVA_CLIENT_ID') and os.getenv('STRAVA_CLIENT_SECRET') and os.getenv('RWUN_TOKEN_KEY') and os.getenv('PUBLIC_BASE_URL','').startswith('https://'))
    def cipher(self):
        try:return Fernet(os.environ['RWUN_TOKEN_KEY'].encode())
        except Exception:raise ApiError(503,'Operator must configure the Strava token encryption key')
    def strava_token(self,user):
        with self.db() as db:row=db.execute('SELECT payload FROM strava WHERE user=?',(user,)).fetchone()
        if not row:raise ApiError(409,'Connect Strava first')
        token=json.loads(self.cipher().decrypt(row[0]))
        if token['expires_at']<time.time()+60:
            token=self.exchange({'grant_type':'refresh_token','refresh_token':token['refresh_token']});self.save_token(user,token)
        return token['access_token']
    def exchange(self,data):
        data.update(client_id=os.getenv('STRAVA_CLIENT_ID'),client_secret=os.getenv('STRAVA_CLIENT_SECRET'))
        return self.remote('https://www.strava.com/oauth/token',urlencode(data).encode(),{'Content-Type':'application/x-www-form-urlencoded'})
    @staticmethod
    def remote(url,data=None,headers=None):
        try:
            with urlopen(Request(url,data=data,headers=headers or {}),timeout=25) as response:
                raw=response.read(12*1024*1024+1)
                if len(raw)>12*1024*1024:raise ApiError(502,'Provider response too large')
                return json.loads(raw)
        except HTTPError as error:raise ApiError(502,'Provider rejected the request; reconnect or try later') from error
        except (TimeoutError,OSError,ValueError) as error:raise ApiError(502,'Provider is unavailable; try later') from error
    def save_token(self,user,token):
        with self.db() as db:db.execute('INSERT OR REPLACE INTO strava VALUES(?,?)',(user,self.cipher().encrypt(json.dumps(token).encode())))

class Handler(BaseHTTPRequestHandler):
    server_version='OpenRun'
    def log_message(self,*args):pass # Authorization codes/tokens are never logged.
    @property
    def service(self):return self.server.service
    def send(self,status,data,headers=None,content_type='application/json'):
        raw=json.dumps(data).encode() if content_type=='application/json' else data
        self.send_response(status);self.send_header('Content-Type',content_type);self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Content-Length',str(len(raw)))
        for key,value in (headers or {}).items():self.send_header(key,value)
        self.end_headers();self.wfile.write(raw)
    def body(self,limit=8192):
        try:length=int(self.headers.get('Content-Length','0'))
        except ValueError:raise ApiError(400,'Invalid length')
        if length<0 or length>limit:raise ApiError(413,'Request too large')
        return self.rfile.read(length)
    def do_GET(self):self.dispatch('GET')
    def do_POST(self):self.dispatch('POST')
    def do_PUT(self):self.dispatch('PUT')
    def do_DELETE(self):self.dispatch('DELETE')
    def dispatch(self,method):
        try:self.route(method)
        except RaceServiceError as error:self.send(error.status,{'error':error.message})
        except ApiError as error:self.send(error.status,{'error':error.message})
        except (ValueError,TypeError,KeyError):self.send(400,{'error':'Invalid request'})
        except Exception:self.send(500,{'error':'The service could not complete the request'})
    def route(self,method):
        parsed=urlparse(self.path);path=parsed.path;svc=self.service
        if method=='GET' and path=='/v1/races':return self.send(200,svc.races.races(parse_qs(parsed.query)))
        if method=='GET' and path=='/health':return self.send(200,{'status':'ok'})
        if method=='POST' and path in ('/v1/register','/v1/login'):return self.send(200,svc.account(path,json.loads(self.body()),self.client_address[0]))
        if method=='GET' and path=='/strava/callback':
            if not svc.strava_ready():raise ApiError(503,'Strava is not configured')
            query=parse_qs(parsed.query);state=query.get('state',[''])[0]
            with svc.lock,svc.db() as db:
                row=db.execute('SELECT user FROM oauth WHERE state=? AND expires>?',(hashlib.sha256(state.encode()).hexdigest(),time.time())).fetchone();db.execute('DELETE FROM oauth WHERE state=?',(hashlib.sha256(state.encode()).hexdigest(),))
            if not row:raise ApiError(400,'Authorization expired or invalid')
            if 'error' in query:raise ApiError(400,'Strava authorization was declined')
            token=svc.exchange({'grant_type':'authorization_code','code':query.get('code',[''])[0]});svc.save_token(row[0],token)
            return self.send(200,b'Connected. Return to OpenRun and tap Refresh status.',content_type='text/plain')
        user=svc.authenticate(self.headers.get('Authorization',''))
        if method=='POST' and path=='/v1/coach/explain':return self.send(200,svc.races.explain(user,json.loads(self.body())))
        if path=='/v1/logout' and method=='POST':
            with svc.db() as db:db.execute('DELETE FROM sessions WHERE token=?',(hashlib.sha256(self.headers.get('Authorization')[7:].encode()).hexdigest(),))
            return self.send(200,{'signed_out':True})
        if path=='/v1/account' and method=='DELETE':
            with svc.db() as db:
                for table in ('sessions','backups','strava','oauth'):db.execute('DELETE FROM '+table+' WHERE user=?',(user,))
                db.execute('DELETE FROM users WHERE id=?',(user,))
            return self.send(200,{'deleted':True})
        if path=='/v1/backup':
            if method=='GET':
                with svc.db() as db:row=db.execute('SELECT revision,payload FROM backups WHERE user=?',(user,)).fetchone()
                if not row:raise ApiError(404,'No cloud backup yet')
                return self.send(200,row[1],{'ETag':str(row[0])},'application/octet-stream')
            if method=='PUT':
                payload=self.body(MAX_BACKUP)
                if len(payload)<48:raise ApiError(400,'Encrypted backup is too small')
                try:expected=int(self.headers.get('If-Match','-1').strip('"'))
                except ValueError:raise ApiError(400,'Invalid backup revision')
                with svc.lock,svc.db() as db:
                    row=db.execute('SELECT revision FROM backups WHERE user=?',(user,)).fetchone();revision=row[0] if row else 0
                    if revision!=expected:raise ApiError(409,'Cloud backup changed. Export your local backup, then restore cloud data before uploading again.')
                    db.execute('INSERT OR REPLACE INTO backups VALUES(?,?,?,?)',(user,revision+1,payload,int(time.time())))
                return self.send(200,{'revision':revision+1})
        if path=='/v1/integrations' and method=='GET':
            with svc.db() as db:linked=bool(db.execute('SELECT 1 FROM strava WHERE user=?',(user,)).fetchone())
            return self.send(200,{'strava_configured':svc.strava_ready(),'strava_linked':linked,'garmin_connected':False,'garmin_blocker':'Official partner approval, credentials and partner API documentation are required.'})
        if path=='/v1/strava/start' and method=='POST':
            if not svc.strava_ready():raise ApiError(503,'Operator must register a Strava application and configure OAuth credentials, encryption key and HTTPS callback')
            state=secrets.token_urlsafe(32)
            with svc.db() as db:db.execute('INSERT INTO oauth VALUES(?,?,?)',(hashlib.sha256(state.encode()).hexdigest(),user,int(time.time())+600))
            return self.send(200,{'url':'https://www.strava.com/oauth/authorize?'+urlencode({'client_id':os.getenv('STRAVA_CLIENT_ID'),'redirect_uri':os.getenv('PUBLIC_BASE_URL').rstrip('/')+'/strava/callback','response_type':'code','approval_prompt':'auto','scope':'activity:read_all,activity:write','state':state})})
        if path=='/v1/strava' and method=='DELETE':
            token=svc.strava_token(user);svc.remote('https://www.strava.com/oauth/deauthorize',urlencode({'access_token':token}).encode())
            with svc.db() as db:db.execute('DELETE FROM strava WHERE user=?',(user,))
            return self.send(200,{'disconnected':True})
        if path=='/v1/strava/activities' and method=='GET':
            token=svc.strava_token(user);out=[]
            for page in range(1,21):
                rows=svc.remote('https://www.strava.com/api/v3/athlete/activities?'+urlencode({'after':int(time.time())-30*86400,'page':page,'per_page':100}),headers={'Authorization':'Bearer '+token})
                out.extend({key:r.get(key) for key in ('id','sport_type','start_date','distance','elapsed_time','moving_time','name')} for r in rows if r.get('sport_type') in ('Run','TrailRun','VirtualRun'))
                if len(rows)<100:return self.send(200,{'activities':out})
            raise ApiError(502,'Too many activities; contact the operator')
        if path=='/v1/strava/upload' and method=='POST':
            token=svc.strava_token(user);gpx=self.body(10*1024*1024);boundary=secrets.token_hex(16)
            fields={'data_type':'gpx','sport_type':'Run','external_id':'rwun-'+hashlib.sha256(gpx).hexdigest()}
            parts=[]
            for name,value in fields.items():parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
            parts.extend([f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="rwun.gpx"\r\nContent-Type: application/gpx+xml\r\n\r\n'.encode(),gpx,f'\r\n--{boundary}--\r\n'.encode()])
            result=svc.remote('https://www.strava.com/api/v3/uploads',b''.join(parts),{'Authorization':'Bearer '+token,'Content-Type':'multipart/form-data; boundary='+boundary})
            return self.send(200,{'upload_id':result.get('id'),'status':result.get('status'),'error':result.get('error')})
        if path.startswith('/v1/strava/uploads/') and method=='GET':
            identifier=path.rsplit('/',1)[1]
            if not identifier.isdigit():raise ApiError(400,'Invalid upload ID')
            result=svc.remote('https://www.strava.com/api/v3/uploads/'+identifier,headers={'Authorization':'Bearer '+svc.strava_token(user)})
            return self.send(200,{key:result.get(key) for key in ('id','activity_id','status','error')})
        raise ApiError(404,'Unknown endpoint')

def make_server(host='127.0.0.1',port=8000,path='rwun.sqlite3'):
    server=ThreadingHTTPServer((host,port),Handler);server.service=Service(path);return server
if __name__=='__main__':make_server('0.0.0.0',int(os.getenv('PORT','8000')),os.getenv('RWUN_DB','/data/rwun.sqlite3')).serve_forever()
