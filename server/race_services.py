"""Authorised catalogue feeds and optional Gemini explanations. Never generates schedules."""
from __future__ import annotations
import copy,json,math,os,re,threading,time
from collections import defaultdict,deque
from datetime import date
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request,urlopen,build_opener,HTTPRedirectHandler

class RaceServiceError(Exception):
    def __init__(self,status,message):self.status,self.message=status,message

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def remote_json(url,data=None,headers=None):
    if urlparse(url).scheme!='https':raise ValueError('HTTPS required')
    request=Request(url,data=data,headers={'Accept':'application/json','User-Agent':'OpenRun/1.3 catalogue',**(headers or {})})
    with build_opener(NoRedirect).open(request,timeout=25) as response:
        if 'application/json' not in response.headers.get('Content-Type',''):raise ValueError('JSON required')
        raw=response.read(4*1024*1024+1)
        if len(raw)>4*1024*1024:raise ValueError('Response too large')
        return json.loads(raw)

def validate_catalogue(data):
    if not isinstance(data,dict) or not isinstance(data.get('races'),list) or len(data['races'])>5000:raise ValueError('Invalid race list')
    date.fromisoformat(data['updated']);seen=set()
    if not isinstance(data.get('scope'),str) or len(data['scope'])>1000:raise ValueError('Describe catalogue coverage')
    for race in data['races']:
        if not isinstance(race,dict):raise ValueError('Invalid event')
        key=race.get('id')
        if not isinstance(key,str) or not re.fullmatch(r'[A-Za-z0-9:_-]{1,120}',key) or key in seen:raise ValueError('Invalid event ID')
        seen.add(key)
        for k in ('name','city','country','surface'):
            if not isinstance(race.get(k),str) or not race[k].strip() or len(race[k])>180:raise ValueError('Invalid event text')
        for k in ('date','verified'):date.fromisoformat(race[k])
        km=race.get('km')
        if isinstance(km,bool) or not isinstance(km,(int,float)) or not math.isfinite(km) or not 1<=km<=250:raise ValueError('Invalid event distance')
        source=race.get('source','');url=urlparse(source)
        if len(source)>1000 or url.scheme!='https' or not url.hostname or url.username:raise ValueError('Invalid source URL')
    return data

class RaceServices:
    def __init__(self):
        self.lock=threading.RLock();self.calls=defaultdict(deque);self.last_refresh=float("-inf")
        self.catalogue=validate_catalogue(json.loads(Path(__file__).with_name('races.json').read_text()))
    def races(self,query):
        with self.lock:
            stale=False;feed=os.getenv('RWUN_RACE_FEED_URL','')
            if feed and time.monotonic()-self.last_refresh>=900:
                self.last_refresh=time.monotonic()
                try:self.catalogue=validate_catalogue(remote_json(feed))
                except Exception:self.catalogue['refresh_failed']=True
                else:self.catalogue.pop('refresh_failed',None)
            data=copy.deepcopy(self.catalogue);stale=data.pop('refresh_failed',False)
        if stale:data['scope']+=' Refresh unavailable; showing last valid catalogue.'
        # Request parameters are filters, never provider URLs or prompts.
        try:
            q=query.get('q',[''])[0].casefold();country=query.get('country',[''])[0];distance=float(query.get('distance',['0'])[0]);start=date.fromisoformat(query.get('from',['1900-01-01'])[0]);end=date.fromisoformat(query.get('until',['2100-01-01'])[0])
            if len(q)>180 or len(country)>180 or not math.isfinite(distance) or distance<0 or start>end:raise ValueError()
        except Exception:raise RaceServiceError(400,'Invalid race filters')
        data['races']=[r for r in data['races'] if q in (r['name']+' '+r['city']).casefold() and (not country or r['country'].casefold()==country.casefold()) and (not distance or abs(r['km']-distance)<.11) and start<=date.fromisoformat(r['date'])<=end]
        data['ai_configured']=bool(os.getenv('GEMINI_API_KEY') and os.getenv('GEMINI_MODEL'))
        return data
    def explain(self,user,data):
        if not isinstance(data,dict) or data.get('consent') is not True or set(data)!={'consent','summary'}:raise RaceServiceError(400,'Explicit summary-sharing consent is required')
        summary=data['summary'];bounds={'runs28':(0,1000),'km28':(0,5000),'daysSinceLast':(-1,36500),'factor':(.1,1),'goalKm':(5,42.2),'weeks':(4,24)}
        if not isinstance(summary,dict) or set(summary)!=set(bounds)|{'hold'} or type(summary['hold']) is not bool:raise RaceServiceError(400,'Only the documented numeric summary is accepted')
        for k,(low,high) in bounds.items():
            v=summary[k]
            if isinstance(v,bool) or not isinstance(v,(float,int)) or not math.isfinite(v) or not low<=v<=high:raise RaceServiceError(400,'Invalid training summary')
        for k in ('runs28','daysSinceLast','weeks'):
            if summary[k]!=int(summary[k]):raise RaceServiceError(400,'Invalid summary count')
        key=os.getenv('GEMINI_API_KEY');model=os.getenv('GEMINI_MODEL','')
        if not key or not re.fullmatch(r'[A-Za-z0-9.-]{1,100}',model):raise RaceServiceError(503,'AI explanations are unavailable. The operator must configure Gemini credentials and a supported model on the server. Local planning remains available.')
        now=time.monotonic()
        with self.lock:
            calls=self.calls[user]
            while calls and now-calls[0]>=86400:calls.popleft()
            if len(calls)>=20 or sum(t>now-60 for t in calls)>=3:raise RaceServiceError(429,'AI explanation limit reached; try later')
            calls.append(now)
        instruction=('Explain the supplied original running-plan rules in at most 120 words. Do not generate workouts, distances, dates, pace prescriptions or a finish-time prediction. Do not invent evidence, infer unrecorded activity, diagnose conditions, claim professional review, or clear a training hold. Factor below one means a reviewed reduction; no automatic increase. A hold means no changes. daysSinceLast=-1 means missing history. km28/4 is an observed average, not complete evidence of fitness. Explain uncertainty and use plain language.')
        payload={'systemInstruction':{'parts':[{'text':instruction}]},'contents':[{'role':'user','parts':[{'text':json.dumps(summary,allow_nan=False)}]}],'generationConfig':{'maxOutputTokens':512,'temperature':.2}}
        try:
            reply=remote_json('https://generativelanguage.googleapis.com/v1beta/models/'+model+':generateContent',json.dumps(payload).encode(),{'Content-Type':'application/json','x-goog-api-key':key})
            candidate=reply['candidates'][0]
            if candidate.get('finishReason')!='STOP':raise ValueError('Incomplete provider response')
            text='\n'.join(part['text'] for part in candidate['content']['parts'] if 'text' in part and not part.get('thought'))
            if not isinstance(text,str) or not text.strip() or len(text)>3000:raise ValueError('Invalid explanation')
        except Exception:raise RaceServiceError(502,'The AI provider did not return a complete explanation. Your local plan is unchanged.')
        return {'explanation':text,'provider':'Gemini','model':model,'changes_plan':False}
