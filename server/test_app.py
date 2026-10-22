import concurrent.futures,json,os,sys,tempfile,threading,unittest
from urllib.request import Request,urlopen
from urllib.error import HTTPError
sys.path.insert(0,os.path.dirname(__file__))
import app
class ServiceTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.server=app.make_server(port=0,path=self.temp.name+'/test.sqlite');self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.url='http://127.0.0.1:'+str(self.server.server_port)
    def tearDown(self):self.server.shutdown();self.server.server_close();self.temp.cleanup()
    def request(self,path,method='GET',data=None,token=None,headers=None):
        headers=dict(headers or {})
        if isinstance(data,dict):data=json.dumps(data).encode();headers['Content-Type']='application/json'
        if token:headers['Authorization']='Bearer '+token
        try:
            with urlopen(Request(self.url+path,data=data,method=method,headers=headers),timeout=10) as response:return response.status,response.read(),response.headers
        except HTTPError as error:return error.code,error.read(),error.headers
    def account(self,email='runner@example.com'):
        status,raw,_=self.request('/v1/register','POST',{'email':email,'password':'a sufficiently long password'});self.assertEqual(status,200);return json.loads(raw)['token']
    def test_auth_backup_conflict_isolation_delete(self):
        token=self.account();other=self.account('other@example.com')
        self.assertEqual(self.request('/v1/backup')[0],401)
        payload=b'encrypted payload placeholder for transport test only'+b'x'*10
        self.assertEqual(self.request('/v1/backup','PUT',payload,token,{'If-Match':'0'})[0],200)
        self.assertEqual(self.request('/v1/backup','PUT',payload,token,{'If-Match':'0'})[0],409)
        code,data,headers=self.request('/v1/backup',token=token);self.assertEqual((code,data,headers['ETag']),(200,payload,'1'))
        self.assertEqual(self.request('/v1/backup',token=other)[0],404)
        def write(_):return self.request('/v1/backup','PUT',payload,token,{'If-Match':'1'})[0]
        with concurrent.futures.ThreadPoolExecutor(2) as pool:self.assertEqual(sorted(pool.map(write,range(2))),[200,409])
        self.assertEqual(self.request('/v1/account','DELETE',token=token)[0],200)
        self.assertEqual(self.request('/v1/backup',token=token)[0],401)
    def test_login_and_logout(self):
        token=self.account();self.assertEqual(self.request('/v1/login','POST',{'email':'runner@example.com','password':'a sufficiently long password'})[0],200)
        self.assertEqual(self.request('/v1/login','POST',{'email':'runner@example.com','password':'an incorrect long password'})[0],401)
        self.assertEqual(self.request('/v1/logout','POST',b'',token)[0],200);self.assertEqual(self.request('/v1/integrations',token=token)[0],401)
    def test_integration_blockers_and_input_limits(self):
        token=self.account();code,raw,_=self.request('/v1/integrations',token=token);self.assertEqual(code,200);self.assertFalse(json.loads(raw)['garmin_connected'])
        self.assertEqual(self.request('/v1/strava/start','POST',b'',token)[0],503)
        self.assertEqual(self.request('/v1/backup','PUT',b'short',token,{'If-Match':'0'})[0],400)
        self.assertEqual(self.request('/v1/register','POST',{'email':123,'password':[]})[0],400)
    def test_provider_tokens_are_encrypted(self):
        from unittest.mock import patch
        from cryptography.fernet import Fernet
        with patch.dict(os.environ,{'RWUN_TOKEN_KEY':Fernet.generate_key().decode()}):
            self.server.service.save_token('user',{'access_token':'provider-token','refresh_token':'refresh-secret','expires_at':9999999999})
            with self.server.service.db() as db:payload=db.execute('SELECT payload FROM strava WHERE user=?',('user',)).fetchone()[0]
            self.assertNotIn(b'provider-token',payload);self.assertNotIn(b'refresh-secret',payload)
            self.assertEqual(self.server.service.strava_token('user'),'provider-token')
            with self.assertRaises(app.ApiError):self.server.service.strava_token('different-user')
    def test_oauth_state_is_single_use(self):
        state='test-state';import hashlib,time
        with self.server.service.db() as db:db.execute('INSERT INTO oauth VALUES(?,?,?)',(hashlib.sha256(state.encode()).hexdigest(),'u',time.time()+600))
        self.server.service.strava_ready=lambda:True
        self.server.service.exchange=lambda data:{'access_token':'fake-test-token','refresh_token':'fake-refresh','expires_at':9999999999}
        self.server.service.save_token=lambda user,token:None
        self.assertEqual(self.request('/strava/callback?state='+state+'&code=test')[0],200)
        self.assertEqual(self.request('/strava/callback?state='+state+'&code=test')[0],400)
        state='concurrent-state'
        with self.server.service.db() as db:db.execute('INSERT INTO oauth VALUES(?,?,?)',(hashlib.sha256(state.encode()).hexdigest(),'u',time.time()+600))
        with concurrent.futures.ThreadPoolExecutor(2) as pool:codes=list(pool.map(lambda _:self.request('/strava/callback?state='+state+'&code=test')[0],range(2)))
        self.assertEqual(sorted(codes),[200,400])
    def test_race_catalogue_http_and_ai_authentication(self):
        status,raw,_=self.request('/v1/races?country=South%20Africa&distance=21.1');self.assertEqual(status,200);data=json.loads(raw);self.assertEqual(len(data['races']),1);self.assertEqual(data['races'][0]['km'],21.1)
        self.assertEqual(self.request('/v1/races?distance=nan')[0],400)
        payload={'consent':True,'summary':{'runs28':4,'km28':20,'daysSinceLast':15,'factor':.8,'hold':False,'goalKm':21.1,'weeks':14}}
        self.assertEqual(self.request('/v1/coach/explain','POST',payload)[0],401)
        token=self.account()
        from unittest.mock import patch
        with patch.dict(os.environ,{'GEMINI_API_KEY':'','GEMINI_MODEL':''}):self.assertEqual(self.request('/v1/coach/explain','POST',payload,token)[0],503)
        payload['consent']=False;self.assertEqual(self.request('/v1/coach/explain','POST',payload,token)[0],400)
if __name__=='__main__':unittest.main()
