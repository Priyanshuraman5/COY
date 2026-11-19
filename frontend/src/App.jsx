import { useEffect, useRef, useState } from 'react';

const API = 'http://localhost:8080';
const inviteParam = new URLSearchParams(window.location.search).get('invite');
if (inviteParam) localStorage.setItem('coy_invite', inviteParam);
const api = async (path, body, method) => {
  const token = localStorage.getItem('coy_token');
  const res = await fetch(API + path, {
    method: method || (body ? 'POST' : 'GET'),
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401 && token) { localStorage.removeItem('coy_token'); window.location.reload(); }
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || 'Request failed');
  return data;
};
const S = {
  app: { maxWidth: 420, margin: '0 auto', height: '100vh', display: 'flex', flexDirection: 'column', fontFamily: 'system-ui, sans-serif' },
  main: { flex: 1, position: 'relative', overflow: 'hidden', padding: 16 },
  tabs: { display: 'flex', borderTop: '1px solid #ddd' },
  tab: (on) => ({ flex: 1, padding: 14, border: 0, background: 'none', fontWeight: on ? 700 : 400, color: on ? '#e11d48' : '#555' }),
  card: { position: 'absolute', inset: 16, bottom: 90, borderRadius: 20, overflow: 'hidden', boxShadow: '0 6px 24px #0003', background: '#fff', touchAction: 'none', cursor: 'grab' },
  btn: (c) => ({ width: 64, height: 64, borderRadius: '50%', border: 0, fontSize: 28, color: '#fff', background: c }),
};

export default function App() {
  const [tab, setTab] = useState('discover');
  const [loggedIn, setLoggedIn] = useState(!!localStorage.getItem('coy_token'));
  if (!loggedIn) return <div style={S.app}><Login onDone={() => setLoggedIn(true)} /></div>;
  return (
    <div style={S.app}>
      <div style={S.main}>{tab === 'discover' ? <Discover /> : tab === 'crushes' ? <Crushes /> : <div />}</div>
      <nav style={S.tabs}>
        <button style={S.tab(tab === 'home')} onClick={() => setTab('home')}>Home</button>
        <button style={S.tab(tab === 'discover')} onClick={() => setTab('discover')}>Discover</button>
        <button style={S.tab(tab === 'crushes')} onClick={() => setTab('crushes')}>Crushes</button>
        <button style={S.tab(false)} onClick={() => { localStorage.removeItem('coy_token'); setLoggedIn(false); }}>Logout</button>
      </nav>
    </div>
  );
}

function Discover() {
  const [feed, setFeed] = useState(null);
  const [matches, setMatches] = useState([]);
  const [view, setView] = useState('swipe');
  const [matched, setMatched] = useState(null);
  const [drag, setDrag] = useState({ x: 0, active: false });
  const [flying, setFlying] = useState(0);
  const startX = useRef(0);

  const load = () => {
    api('/feed').then(setFeed);
    api('/matches').then(setMatches);
  };
  useEffect(load, []);

  const swipe = async (direction) => {
    const p = feed[0];
    setFlying(direction === 'LIKE' ? 1 : -1);
    const res = await api('/swipe', { profileId: p.id, direction });
    setTimeout(() => {
      setFlying(0); setDrag({ x: 0, active: false });
      setFeed((f) => f.slice(1));
      if (res.match) { setMatched(res.profile); setMatches((m) => [...m, res.profile]); }
    }, 250);
  };

  const onDown = (e) => { startX.current = e.clientX; setDrag({ x: 0, active: true }); e.currentTarget.setPointerCapture(e.pointerId); };
  const onMove = (e) => drag.active && setDrag({ x: e.clientX - startX.current, active: true });
  const onUp = () => {
    if (drag.x > 100) swipe('LIKE');
    else if (drag.x < -100) swipe('PASS');
    else setDrag({ x: 0, active: false });
  };

  if (matched) return (
    <div style={{ textAlign: 'center', paddingTop: 60 }}>
      <h1 style={{ color: '#e11d48' }}>It's a match!</h1>
      <img src={matched.photo} alt="" style={{ width: 200, borderRadius: 20 }} />
      <p>You and {matched.name} liked each other.</p>
      <button style={S.btn('#e11d48')} onClick={() => setMatched(null)}>✓</button>
    </div>
  );

  const header = (
    <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
      <button onClick={() => setView('swipe')} disabled={view === 'swipe'}>Swipe</button>
      <button onClick={() => setView('matches')} disabled={view === 'matches'}>Matches ({matches.length})</button>
    </div>
  );

  if (view === 'matches') return (
    <div>{header}
      {matches.length === 0 && <p>No matches yet.</p>}
      {matches.map((m) => (
        <div key={m.id} style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 10 }}>
          <img src={m.photo} alt="" style={{ width: 56, height: 56, borderRadius: '50%', objectFit: 'cover' }} />
          <div><b>{m.name}, {m.age}</b><div style={{ color: '#666' }}>{m.city}</div></div>
        </div>
      ))}
    </div>
  );

  if (!feed) return <p>Loading…</p>;
  if (feed.length === 0) return <div>{header}<h3>You're all caught up</h3><p>No more profiles right now. Check back later.</p></div>;

  const p = feed[0];
  const x = flying ? flying * 500 : drag.x;
  const tint = x > 40 ? '#16a34a' : x < -40 ? '#dc2626' : 'transparent';
  return (
    <div>{header}
      <div style={{ ...S.card, transform: `translateX(${x}px) rotate(${x / 20}deg)`, transition: drag.active ? 'none' : 'transform .25s', outline: `4px solid ${tint}` }}
        onPointerDown={onDown} onPointerMove={onMove} onPointerUp={onUp}>
        {x > 40 && <b style={{ position: 'absolute', top: 20, left: 20, color: '#16a34a', fontSize: 28 }}>LIKE</b>}
        {x < -40 && <b style={{ position: 'absolute', top: 20, right: 20, color: '#dc2626', fontSize: 28 }}>PASS</b>}
        <img src={p.photo} alt={p.name} draggable={false} style={{ width: '100%', height: '65%', objectFit: 'cover' }} />
        <div style={{ padding: 14 }}>
          <h2 style={{ margin: 0 }}>{p.name}, {p.age}</h2>
          <div style={{ color: '#666' }}>{p.city}</div>
          <p>{p.bio}</p>
          <small>{p.interests.join(' · ')}</small>
        </div>
      </div>
      <div style={{ position: 'absolute', bottom: 12, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 40 }}>
        <button style={S.btn('#dc2626')} onClick={() => swipe('PASS')}>✕</button>
        <button style={S.btn('#16a34a')} onClick={() => swipe('LIKE')}>♥</button>
      </div>
    </div>
  );
}

function Login({ onDone }) {
  const [phone, setPhone] = useState('');
  const [otp, setOtp] = useState('');
  const [step, setStep] = useState('phone');
  const [hint, setHint] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const run = async (fn) => { setErr(''); setBusy(true); try { await fn(); } catch (e) { setErr(e.message); } setBusy(false); };
  const send = () => run(async () => {
    const r = await api('/auth/send-otp', { phone });
    setHint(r.devOtp ? `Demo mode: your OTP is ${r.devOtp}` : '');
    setStep('otp');
  });
  const verify = () => run(async () => {
    const r = await api('/auth/verify-otp', { phone, otp, invite: localStorage.getItem('coy_invite') });
    localStorage.setItem('coy_token', r.token);
    onDone();
  });
  const input = { padding: 12, fontSize: 18, borderRadius: 10, border: '1px solid #ccc', width: '100%', boxSizing: 'border-box' };
  const go = { ...input, border: 0, background: '#e11d48', color: '#fff', fontWeight: 700, marginTop: 12 };

  return (
    <div style={{ padding: 28, marginTop: 80 }}>
      <h1 style={{ color: '#e11d48' }}>Coy</h1>
      {step === 'phone' ? (<>
        <p>Enter your phone number to log in.</p>
        <input style={input} type="tel" inputMode="tel" placeholder="Phone number" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <button style={go} disabled={busy} onClick={send}>Send OTP</button>
      </>) : (<>
        <p>Enter the 6-digit OTP sent to {phone}.</p>
        {hint && <p style={{ color: '#666' }}>{hint}</p>}
        <input style={input} inputMode="numeric" maxLength={6} placeholder="OTP" value={otp} onChange={(e) => setOtp(e.target.value)} />
        <button style={go} disabled={busy} onClick={verify}>Verify and continue</button>
        <button style={{ ...go, background: 'none', color: '#555' }} onClick={() => { setStep('phone'); setOtp(''); }}>Change number</button>
      </>)}
      {err && <p style={{ color: '#dc2626' }}>{err}</p>}
    </div>
  );
}

function Crushes() {
  const [view, setView] = useState('crushes');
  const [me, setMe] = useState(null);
  const [crushes, setCrushes] = useState([]);
  const [invite, setInvite] = useState(null);
  const [plans, setPlans] = useState([]);
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [hint, setHint] = useState('');
  const [msg, setMsg] = useState('');

  const load = () => {
    api('/me').then(setMe);
    api('/crushes').then(setCrushes);
    api('/invite').then(setInvite);
    api('/slots').then((r) => setPlans(r.plans));
  };
  useEffect(load, []);
  useEffect(() => { const t = setInterval(load, 5000); return () => clearInterval(t); }, []);
  const act = async (fn) => { setMsg(''); try { await fn(); } catch (e) { setMsg(e.message); } };

  const sendEmail = () => act(async () => {
    const r = await api('/email/send', { email });
    setHint(r.devOtp ? `Demo mode: your code is ${r.devOtp}` : '');
    setCodeSent(true);
  });
  const verifyEmail = () => act(async () => { await api('/email/verify', { email, code }); setCodeSent(false); load(); setMsg('Email verified'); });
  const addCrush = () => act(async () => {
    if (!window.confirm(`Add ${phone}? Double-check the number. A wrong number can't be fixed later.`)) return;
    const r = await api('/crushes', { phone });
    setPhone(''); load();
    setMsg(r.mutual ? "It's mutual! 💘"
      : r.pending ? `Added. You can undo within ${r.undoSeconds}s. Nobody is notified until then.`
      : "Added. They'll only find out if they add you too.");
  });
  const undo = (p) => act(async () => {
    await api('/crushes/' + encodeURIComponent(p), null, 'DELETE');
    load(); setMsg('Crush removed. Nobody was notified.');
  });
  const buy = () => act(() => api('/slots/purchase', {}));

  const box = { padding: 12, borderRadius: 12, border: '1px solid #ddd', marginBottom: 12 };
  const inp = { padding: 10, fontSize: 16, borderRadius: 8, border: '1px solid #ccc', width: '100%', boxSizing: 'border-box', marginBottom: 8 };
  const btn = { padding: '10px 14px', border: 0, borderRadius: 8, background: '#e11d48', color: '#fff', fontWeight: 700 };
  if (!me) return <p>Loading…</p>;

  return (
    <div style={{ height: '100%', overflowY: 'auto' }}>
      <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
        {['crushes', 'chats', 'invite', 'slots'].map((v) => (
          <button key={v} onClick={() => { setView(v); setMsg(''); }} disabled={view === v}>{v[0].toUpperCase() + v.slice(1)}</button>
        ))}
      </div>
      {msg && <p style={{ color: '#be123c' }}>{msg}</p>}

      {view === 'crushes' && <>
        {!me.emailVerified && (
          <div style={box}>
            <b>Verify your email to add crushes</b>
            <input style={{ ...inp, marginTop: 8 }} type="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} />
            {!codeSent ? <button style={btn} onClick={sendEmail}>Send code</button> : <>
              {hint && <p style={{ color: '#666' }}>{hint}</p>}
              <input style={inp} inputMode="numeric" maxLength={6} placeholder="6-digit code" value={code} onChange={(e) => setCode(e.target.value)} />
              <button style={btn} onClick={verifyEmail}>Verify</button>
            </>}
          </div>
        )}
        <div style={box}>
          <b>Add a crush</b> <small>({me.slots.used}/{me.slots.total} slots used)</small>
          <input style={{ ...inp, marginTop: 8 }} type="tel" placeholder="Their phone number" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <button style={btn} onClick={addCrush}>Add secretly</button>
          {me.slots.free === 0 && <p>Out of slots. <a href="#slots" onClick={(e) => { e.preventDefault(); setView('slots'); }}>Get more</a></p>}
        </div>
        {crushes.length === 0 && <p style={{ color: '#666' }}>No crushes yet.</p>}
        {crushes.map((c) => (
          <div key={c.phone} style={{ ...box, display: 'flex', justifyContent: 'space-between' }}>
            <span>{c.phone}</span>
            {c.pending
              ? <span style={{ color: '#b45309' }}>Undo window: {c.secondsLeft}s <button onClick={() => undo(c.phone)}>Undo</button></span>
              : <b style={{ color: c.mutual ? '#16a34a' : '#888' }}>{c.mutual ? 'Mutual 💘' : 'Secret'}</b>}
          </div>
        ))}
      </>}

      {view === 'chats' && <Chats />}

      {view === 'invite' && invite && (
        <div style={box}>
          <b>Invite friends</b>
          <p>Each friend who joins through your link gives you +1 crush slot.</p>
          <input style={inp} readOnly value={invite.link} onFocus={(e) => e.target.select()} />
          <button style={btn} onClick={() => navigator.clipboard.writeText(invite.link).then(() => setMsg('Link copied'))}>Copy link</button>
          <p>Friends joined: <b>{invite.joined}</b></p>
        </div>
      )}

      {view === 'slots' && <>
        <p>You have <b>{me.slots.total}</b> slots ({me.slots.used} used). Slots can also be earned by inviting friends.</p>
        {plans.map((p) => (
          <div key={p.name} style={{ ...box, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span><b>{p.name}</b><br />+{p.slots} slot{p.slots > 1 ? 's' : ''}</span>
            <button style={btn} onClick={buy}>{p.price}</button>
          </div>
        ))}
        <small style={{ color: '#666' }}>Demo only: purchases are disabled.</small>
      </>}
    </div>
  );
}

function Chats() {
  const [list, setList] = useState([]);
  const [open, setOpen] = useState(null);
  const [chat, setChat] = useState(null);
  const [text, setText] = useState('');
  const [err, setErr] = useState('');

  const loadList = () => api('/chats').then(setList).catch((e) => setErr(e.message));
  const loadChat = (id) => api('/chats/' + id).then(setChat).catch((e) => setErr(e.message));
  useEffect(() => { loadList(); }, []);
  useEffect(() => {
    if (!open) return undefined;
    loadChat(open);
    const t = setInterval(() => loadChat(open), 4000);
    return () => clearInterval(t);
  }, [open]);

  const send = async () => {
    if (!text.trim()) return;
    setErr('');
    try { await api(`/chats/${open}/messages`, { body: text }); setText(''); loadChat(open); } catch (e) { setErr(e.message); }
  };
  const reveal = async () => { setErr(''); try { setChat(await api(`/chats/${open}/reveal`, {})); } catch (e) { setErr(e.message); } };
  const hoursLeft = (t) => Math.max(0, Math.round((t - Date.now()) / 3600000));
  const box = { padding: 12, borderRadius: 12, border: '1px solid #ddd', marginBottom: 10 };

  if (!open) return (
    <div>
      {err && <p style={{ color: '#be123c' }}>{err}</p>}
      {list.length === 0 && <p style={{ color: '#666' }}>No chats yet. A chat opens when you and a crush have both added each other (after the undo window).</p>}
      {list.map((c) => (
        <div key={c.id} style={{ ...box, cursor: 'pointer' }} onClick={() => { setChat(null); setOpen(c.id); }}>
          <b>{c.revealed ? c.email : c.alias}</b>
          <div style={{ color: '#666' }}>{c.ended ? 'Ended' : c.revealed ? 'Identity revealed' : `${hoursLeft(c.expiresAt)}h left`}</div>
        </div>
      ))}
    </div>
  );

  if (!chat) return <p>Loading…</p>;
  return (
    <div>
      <button onClick={() => { setOpen(null); loadList(); }}>← Back</button>
      <p>You are <b>{chat.myAlias}</b>, chatting with <b>{chat.revealed ? chat.email : chat.alias}</b>.</p>
      {err && <p style={{ color: '#be123c' }}>{err}</p>}
      {chat.ended ? (
        <p>This chat ended. Nobody revealed in time, so neither of you learns who the other was.</p>
      ) : (<>
        <div style={{ ...box, minHeight: 160, maxHeight: 260, overflowY: 'auto' }}>
          {chat.messages.length === 0 && <span style={{ color: '#888' }}>Say hi. {hoursLeft(chat.expiresAt)}h left.</span>}
          {chat.messages.map((m, i) => (
            <div key={i} style={{ textAlign: m.mine ? 'right' : 'left', margin: '4px 0' }}>
              <span style={{ background: m.mine ? '#fecdd3' : '#eee', padding: '6px 10px', borderRadius: 12, display: 'inline-block' }}>{m.body}</span>
            </div>
          ))}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <input style={{ flex: 1, padding: 10, borderRadius: 8, border: '1px solid #ccc' }} value={text}
            onChange={(e) => setText(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && send()} placeholder="Message" />
          <button onClick={send}>Send</button>
        </div>
        <div style={{ marginTop: 12 }}>
          {chat.revealed ? <b>Both revealed. Their email: {chat.email}</b>
            : chat.iRevealed ? <span>You asked to reveal. Identities show only if they do too.</span>
            : <button onClick={reveal}>Reveal myself</button>}
          <p style={{ color: '#666', fontSize: 13 }}>Identities are shown only if both of you press reveal.</p>
        </div>
      </>)}
    </div>
  );
}
