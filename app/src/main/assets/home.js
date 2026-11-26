/* Pravah home: draggable shortcuts, wallpaper picker, rotating-globe intro.
   Loaded after the main script in ui.html (shares its globals: $, S, N, esc, favUrl, LOGO, host). */
(function(){
'use strict';
/* shared gradients for the Instagram / Telegram icons (must live in a rendered element) */
(function(){
  const d=document.createElement('div');
  d.style.cssText='position:absolute;width:0;height:0;overflow:hidden';
  d.innerHTML='<svg width="0" height="0"><defs><radialGradient id="igb" cx="30%" cy="107%" r="140%"><stop offset="0" stop-color="#FED576"/><stop offset=".26" stop-color="#F47133"/><stop offset=".61" stop-color="#BC3081"/><stop offset="1" stop-color="#4C63D2"/></radialGradient><linearGradient id="tgb" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#37AEE2"/><stop offset="1" stop-color="#1E96C8"/></linearGradient></defs></svg>';
  document.body.appendChild(d);
})();


/* ================= rotating globe (only the globe moves; rings, background, text stay frozen) ================= */
const GL={ready:false,loading:false,cbs:[],tex:null,shade:null,Wt:0,Ht:0};
function glImg(src,cb){const i=new Image();i.onload=()=>cb(i);i.src=src}
function glPx(i,w,h){const c=document.createElement('canvas');c.width=w;c.height=h;const x=c.getContext('2d');x.drawImage(i,0,0,w,h);return x.getImageData(0,0,w,h).data}
function glInit(cb){
  if(!window.GLOBE)return;
  if(GL.ready){cb();return}
  GL.cbs.push(cb);if(GL.loading)return;GL.loading=true;
  glImg(window.GLOBE.tex,ti=>{
    GL.Wt=ti.naturalWidth;GL.Ht=ti.naturalHeight;GL.tex=glPx(ti,GL.Wt,GL.Ht);
    glImg(window.GLOBE.shade,si=>{GL.shade=glPx(si,304,304);GL.ready=true;GL.cbs.splice(0).forEach(f=>f())});
  });
}
function makeGlobe(cv){
  const N2=cv.width,Rc=N2/2,ctx=cv.getContext('2d'),Wt=GL.Wt,Ht=GL.Ht,tex=GL.tex,shade=GL.shade;
  const tmp=[];
  for(let y=0;y<N2;y++)for(let x=0;x<N2;x++){
    const nx=(x+.5-Rc)/Rc,ny=(Rc-y-.5)/Rc,r2=nx*nx+ny*ny;
    if(r2<=.95*.95)tmp.push([x,y,nx,ny,r2])
  }
  const n=tmp.length,idx=new Int32Array(n),vr=new Int32Array(n),vf=new Float32Array(n),u0=new Float32Array(n),sv=new Float32Array(n);
  for(let k=0;k<n;k++){
    const t=tmp[k],x=t[0],y=t[1],nz=Math.sqrt(Math.max(0,1-t[4]));
    const lat=Math.asin(Math.max(-1,Math.min(1,t[3]))),lam=Math.atan2(t[2],nz);
    idx[k]=(y*N2+x)*4;
    const v=(0.5-lat/Math.PI)*Ht-.5,v0=Math.floor(v);vf[k]=v-v0;vr[k]=Math.max(0,Math.min(Ht-2,v0));
    u0[k]=((lam+Math.PI)/(2*Math.PI))*Wt;
    const sx=Math.min(303,Math.floor((x+.5)/N2*304)),sy=Math.min(303,Math.floor((y+.5)/N2*304));
    sv[k]=shade[(sy*304+sx)*4]/127;
  }
  const out=ctx.createImageData(N2,N2),od=out.data;
  return{Wt,frame(shift){
    for(let k=0;k<n;k++){
      let u=u0[k]-shift;u=((u%Wt)+Wt)%Wt;
      const ui=u|0,uf=u-ui,x0=ui,x1=(ui+1)%Wt,r0=vr[k]*Wt,r1=r0+Wt,fy=vf[k],s=sv[k];
      const a=(r0+x0)*4,b=(r0+x1)*4,c=(r1+x0)*4,d=(r1+x1)*4,w00=(1-uf)*(1-fy),w10=uf*(1-fy),w01=(1-uf)*fy,w11=uf*fy,o=idx[k];
      od[o]=(tex[a]*w00+tex[b]*w10+tex[c]*w01+tex[d]*w11)*s;
      od[o+1]=(tex[a+1]*w00+tex[b+1]*w10+tex[c+1]*w01+tex[d+1]*w11)*s;
      od[o+2]=(tex[a+2]*w00+tex[b+2]*w10+tex[c+2]*w01+tex[d+2]*w11)*s;
      od[o+3]=255;
    }
    ctx.putImageData(out,0,0);
  }};
}
/* intro: one turn every 7 s while the intro is on screen */
(function(){
  const cv=document.getElementById('globeCv'),intro=document.getElementById('intro');
  if(!cv||!intro)return;
  glInit(()=>{
    const g=makeGlobe(cv);g.frame(0);let t0=0;
    const loop=t=>{if(!intro.isConnected)return;if(!t0)t0=t;g.frame(((t-t0)/7000)*g.Wt);requestAnimationFrame(loop)};
    requestAnimationFrame(loop);
  });
})();
/* every <canvas data-globe> (start page, About) keeps turning while it is on screen */
(function(){
  const t0=Date.now();
  glInit(()=>{
    setInterval(()=>{
      if(document.hidden||document.getElementById('intro'))return;
      document.querySelectorAll('canvas[data-globe]').forEach(cv=>{
        if(!cv.isConnected||cv.offsetParent===null)return;
        if(!cv._g)cv._g=makeGlobe(cv);
        cv._g.frame((((Date.now()-t0)/10000)%1)*cv._g.Wt);
      });
    },42);
  });
})();

/* ================= wallpapers (drawn in code, no image files) ================= */
const rng=s=>()=>{s=(s*1664525+1013904223)>>>0;return s/4294967296};
const svgUrl=s=>"url('data:image/svg+xml,"+encodeURIComponent(s).replace(/'/g,'%27')+"')";
const W_=400,H_=800;
const head=(defs,body)=>`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W_} ${H_}" preserveAspectRatio="xMidYMid slice"><defs>${defs}</defs>${body}</svg>`;
const lg=(id,cols,x2=0,y2=1)=>`<linearGradient id="${id}" x1="0" y1="0" x2="${x2}" y2="${y2}">${cols.map((c,i)=>`<stop offset="${(i/(cols.length-1)).toFixed(2)}" stop-color="${c}"/>`).join('')}</linearGradient>`;

function tech(o){
  const r=rng(o.seed);let b='',d=lg('bg',o.bg,.4,1);
  b+=`<rect width="${W_}" height="${H_}" fill="url(#bg)"/>`;
  if(o.glow)b+=`<circle cx="${o.glow[0]}" cy="${o.glow[1]}" r="${o.glow[2]}" fill="${o.glow[3]}" opacity=".55" filter="url(#bl)"/>`;
  d+=`<filter id="bl" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="55"/></filter>`;
  if(o.lines){
    for(let i=0;i<46;i++){
      const k=i/46,y=o.lines.y+k*o.lines.h,a=o.lines.amp*(1-Math.abs(k-.5)*1.1);
      b+=`<path d="M-10 ${y+a*1.6} C 90 ${y-a*2} 220 ${y+a*2.6} 410 ${y-a*1.2}" fill="none" stroke="${o.lines.c}" stroke-width=".7" opacity="${(.15+.7*(1-Math.abs(k-.5)*1.6)).toFixed(2)}"/>`;
    }
  }
  (o.rib||[]).forEach((p,i)=>{
    const g='r'+i;d+=lg(g,p.c,1,.3);
    const y=p.y,a=p.a,t=p.t;
    b+=`<path d="M-20 ${y} C 90 ${y-a} 200 ${y+a*1.3} 420 ${y-a*.4} L 420 ${y-a*.4+t} C 200 ${y+a*1.3+t*1.4} 90 ${y-a+t*.5} -20 ${y+t} Z" fill="url(#${g})" opacity="${p.o||.9}" transform="rotate(${p.rot||0} 200 400)"/>`;
  });
  return head(d,b);
}
function mountain(o){
  const r=rng(o.seed);let d=lg('sky',o.sky),b=`<rect width="${W_}" height="${H_}" fill="url(#sky)"/>`;
  if(o.stars)for(let i=0;i<70;i++)b+=`<circle cx="${r()*W_}" cy="${r()*H_*.55}" r="${.5+r()*1}" fill="#fff" opacity="${.25+r()*.65}"/>`;
  if(o.moon){b+=`<circle cx="${o.moon[0]}" cy="${o.moon[1]}" r="9" fill="#f4f1e6" opacity=".95"/>`;if(o.crescent)b+=`<circle cx="${o.moon[0]+5}" cy="${o.moon[1]-3}" r="9" fill="${o.crescent}"/>`}
  o.ridges.forEach((rg,k)=>{
    const base=H_*(rg.y-.13),amp=rg.amp*1.3,ph=r()*6;let p=`M0 ${H_} L0 ${base}`;const pts=[];
    for(let x=0;x<=W_+20;x+=18){const e=.55+.45*Math.sin(x/W_*Math.PI*rg.f+ph);const y=base-amp*e*(.3+.7*Math.pow(r(),1.4));pts.push([x,y]);p+=` L${x} ${y.toFixed(1)}`}
    p+=` L${W_} ${H_} Z`;
    if(rg.snow){b+=`<path d="${p}" fill="${rg.snow}"/><path d="${p}" transform="translate(0 ${rg.cap||16})" fill="${rg.c}"/>`}
    else b+=`<path d="${p}" fill="${rg.c}"/>`;
  });
  return head(d,b);
}
function modern(o){
  const r=rng(o.seed);let d=lg('sky',o.sky),b=`<rect width="${W_}" height="${H_}" fill="url(#sky)"/>`;
  if(o.kind==='pyramid'){
    d+=lg('fa',o.a,1,1)+lg('fb',o.b,1,1);
    b+=`<polygon points="-60,${H_} 190,${H_*.62} 190,${H_}" fill="url(#fa)"/><polygon points="190,${H_*.62} 520,${H_} 190,${H_}" fill="url(#fb)"/>`;
    for(let i=1;i<14;i++){const t=i/14;b+=`<line x1="${-60+250*t}" y1="${H_-(H_*.38)*t}" x2="${190+330*t}" y2="${H_*.62+(H_*.38)*t}" stroke="#fff" stroke-opacity=".12"/>`}
    for(let i=1;i<9;i++){b+=`<line x1="${190-250*i/9}" y1="${H_-(H_*.38)*i/9+ (i*2)}" x2="${190-250*i/9}" y2="${H_}" stroke="#fff" stroke-opacity=".12"/>`}
  }else if(o.kind==='tower'){
    d+=lg('fa',o.a,1,0)+lg('fb',o.b,1,0);
    b+=`<polygon points="120,${H_} 270,${H_*.7} 420,${H_} " fill="url(#fa)"/><polygon points="270,${H_*.7} 420,${H_} 270,${H_}" fill="url(#fb)"/>`;
    for(let i=0;i<22;i++){const y=H_*.7+i*(H_*.3/22);b+=`<line x1="${270-(y-H_*.7)*.5}" y1="${y}" x2="${270+(y-H_*.7)*.5}" y2="${y}" stroke="#000" stroke-opacity=".28" stroke-width="2"/>`}
  }else if(o.kind==='slab'){
    d+=lg('fa',o.a,0,1);
    b+=`<g transform="rotate(${o.rot||-14} 200 500)"><rect x="120" y="470" width="420" height="520" fill="url(#fa)"/>`;
    for(let i=0;i<16;i++)b+=`<rect x="120" y="${480+i*26}" width="420" height="9" fill="${o.line}" opacity=".85"/>`;
    b+=`</g>`;
  }else if(o.kind==='balcony'){
    for(let i=0;i<9;i++){const y=140+i*76,sh=i%2?18:0;
      b+=`<rect x="${-20+sh}" y="${y}" width="470" height="52" rx="26" fill="${o.w}"/><rect x="${-20+sh+16}" y="${y+10}" width="440" height="32" rx="16" fill="${o.dk}" opacity=".9"/>`;
      for(let j=0;j<10;j++)b+=`<rect x="${sh+j*48}" y="${y+10}" width="3" height="32" fill="${o.w}" opacity=".85"/>`}
  }else if(o.kind==='palm'){
    const ox=o.ox,oy=o.oy;
    for(let f=0;f<8;f++){
      const ang=(o.a0+f*(o.span/7))*Math.PI/180,len=330+r()*80,curl=(f%2?1:-1)*(60+r()*50);
      const ex=ox+Math.cos(ang)*len,ey=oy+Math.sin(ang)*len,cx=ox+Math.cos(ang)*len*.5+Math.sin(ang)*curl,cy=oy+Math.sin(ang)*len*.5-Math.cos(ang)*curl;
      b+=`<path d="M${ox} ${oy} Q${cx} ${cy} ${ex} ${ey}" fill="none" stroke="${o.leaf}" stroke-width="3"/>`;
      for(let t=.08;t<1;t+=.045){
        const x=(1-t)*(1-t)*ox+2*(1-t)*t*cx+t*t*ex,y=(1-t)*(1-t)*oy+2*(1-t)*t*cy+t*t*ey;
        const dx=2*(1-t)*(cx-ox)+2*t*(ex-cx),dy=2*(1-t)*(cy-oy)+2*t*(ey-cy),m=Math.hypot(dx,dy),tx=dx/m,ty=dy/m,l=44*(1-t*.6);
        for(const sgn of[-1,1]){const nx=-ty*sgn,ny=tx*sgn;b+=`<line x1="${x}" y1="${y}" x2="${x+(nx*.9+tx*.5)*l}" y2="${y+(ny*.9+ty*.5)*l}" stroke="${o.leaf}" stroke-width="2.4" stroke-linecap="round"/>`}
      }
    }
  }
  return head(d,b);
}

function pravah(o){
  const d=lg('bg',o.bg,.3,1)+'<filter id="bl" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="60"/></filter>';
  let b=`<rect width="${W_}" height="${H_}" fill="url(#bg)"/>`;
  o.glows.forEach(g=>{b+=`<circle cx="${g[0]}" cy="${g[1]}" r="${g[2]}" fill="${g[3]}" opacity="${g[4]}" filter="url(#bl)"/>`});
  b+=`<g fill="none" stroke="#fff" stroke-width="1.3" opacity=".10"><ellipse cx="200" cy="300" rx="330" ry="125" transform="rotate(-22 200 300)"/><ellipse cx="200" cy="300" rx="270" ry="98" transform="rotate(-22 200 300)"/><ellipse cx="200" cy="620" rx="340" ry="130" transform="rotate(18 200 620)"/></g>`;
  const r=rng(o.seed);for(let i=0;i<46;i++)b+=`<circle cx="${r()*W_}" cy="${r()*H_}" r="${.5+r()*1.1}" fill="#fff" opacity="${.12+r()*.5}"/>`;
  return head(d,b);
}
const WP=[
 /* Pravah */
 {id:'p0',c:'Pravah',n:'Pravah',f:()=>pravah({seed:41,bg:['#0A2C96','#0A1F6B','#050D30'],glows:[[30,740,230,'#FF9933',.50],[390,780,250,'#138808',.52],[320,90,210,'#2F6BFF',.55]]})},
 {id:'p1',c:'Pravah',n:'Sunrise',f:()=>pravah({seed:42,bg:['#2A0E4F','#5A1A5C','#1A0A2E'],glows:[[60,700,260,'#FF9933',.62],[340,120,200,'#E84393',.45],[380,640,200,'#FFC857',.35]]})},
 {id:'p2',c:'Pravah',n:'Emerald',f:()=>pravah({seed:43,bg:['#04332B','#06503F','#021B17'],glows:[[350,700,260,'#19C37D',.5],[40,140,210,'#2F6BFF',.4],[60,740,200,'#FF9933',.3]]})},
 {id:'p3',c:'Pravah',n:'Midnight',f:()=>pravah({seed:44,bg:['#12082E','#1F0F5C','#06031A'],glows:[[320,110,230,'#7B5BFF',.55],[40,760,230,'#2F6BFF',.45],[380,520,160,'#E84393',.3]]})},
 /* Tech */
 {id:'t1',c:'Tech',n:'Aurora',f:()=>tech({seed:1,bg:['#14063a','#05010f','#1a0b4d'],glow:[120,260,170,'#d4157a'],rib:[{y:260,a:120,t:60,c:['#ff3fa4','#6a2bff','#0b0a3a'],o:.9,rot:-62},{y:420,a:90,t:40,c:['#2a3cff','#a21cff','#0b0a3a'],o:.7,rot:-62}]})},
 {id:'t2',c:'Tech',n:'Slate',f:()=>tech({seed:2,bg:['#000','#020617','#03203f'],glow:[330,720,230,'#0a6dd6'],rib:[{y:560,a:-60,t:120,c:['#0a2a66','#0aa0ff','#000'],o:.85,rot:-38}]})},
 {id:'t3',c:'Tech',n:'Indigo',f:()=>tech({seed:3,bg:['#0b0420','#1d1190','#2c6bff'],glow:[260,560,220,'#7b5bff'],rib:[{y:430,a:150,t:80,c:['#1a0b6e','#4f7bff','#9ad0ff'],o:.85,rot:-12},{y:620,a:100,t:70,c:['#0b0a40','#3a2cff','#7bc8ff'],o:.75,rot:8}]})},
 {id:'t4',c:'Tech',n:'Violet',f:()=>tech({seed:4,bg:['#0a0224','#1f0a63','#0a0224'],glow:[300,250,180,'#8a3dff'],rib:[{y:330,a:170,t:130,c:['#2a0f8c','#b04dff','#1b0a60'],o:.8,rot:-48}]})},
 {id:'t5',c:'Tech',n:'Ribbon',f:()=>tech({seed:5,bg:['#071a3a','#2a4a86','#0a0a2a'],glow:[100,500,200,'#ff3a6e'],rib:[{y:360,a:140,t:70,c:['#ff5a8a','#8a3dff','#2a7cff'],o:.95,rot:-58},{y:520,a:70,t:50,c:['#2a7cff','#0a0a2a','#ff3a6e'],o:.8,rot:-58}]})},
 {id:'t6',c:'Tech',n:'Flame',f:()=>tech({seed:6,bg:['#070710','#0d0d1c','#05050c'],glow:[150,420,160,'#ff6a00'],rib:[{y:380,a:190,t:90,c:['#ff8a1f','#6a4dff','#1a1a5a'],o:.95,rot:-40},{y:430,a:150,t:40,c:['#ff4a00','#c04dff','#2a2a7a'],o:.8,rot:-40}]})},
 {id:'t7',c:'Tech',n:'Waves',f:()=>tech({seed:7,bg:['#000','#06060e','#000'],glow:[220,360,170,'#7a3cff'],lines:{y:280,h:300,amp:45,c:'#d9a8ff'}})},
 {id:'t8',c:'Tech',n:'Orbit',f:()=>tech({seed:8,bg:['#02061f','#062a73','#021035'],glow:[330,620,230,'#0aa0ff'],rib:[{y:600,a:-130,t:150,c:['#1a8cff','#0a3a99','#021035'],o:.9,rot:-20},{y:700,a:-90,t:120,c:['#4ab4ff','#0a54c8','#021035'],o:.8,rot:-20}]})},
 /* Nature */
 {id:'n1',c:'Nature',n:'Night peak',f:()=>mountain({seed:11,sky:['#030a1d','#0a1c3a'],stars:1,ridges:[{y:.78,amp:120,f:1.1,c:'#0b1424',snow:'#e8eef7',cap:14},{y:.9,amp:70,f:2,c:'#02050a'}]})},
 {id:'n2',c:'Nature',n:'Grey dusk',f:()=>mountain({seed:12,sky:['#5b6574','#8c94a0','#a9afb8'],ridges:[{y:.7,amp:150,f:1.4,c:'#3c4654',snow:'#f1f4f8',cap:18},{y:.86,amp:110,f:2.2,c:'#1c232d',snow:'#cfd6de',cap:10}]})},
 {id:'n3',c:'Nature',n:'Clear sky',f:()=>mountain({seed:13,sky:['#2a8a9c','#7cc4c6','#d6ddd2'],moon:[205,150],ridges:[{y:.72,amp:140,f:1.3,c:'#2b3946',snow:'#ffffff',cap:20},{y:.9,amp:90,f:2.4,c:'#16202a'}]})},
 {id:'n4',c:'Nature',n:'Teal rock',f:()=>mountain({seed:14,sky:['#1e3a42','#2d5560','#41707a'],ridges:[{y:.6,amp:210,f:.9,c:'#1d2a30',snow:'#f4f7f8',cap:22},{y:.82,amp:110,f:1.6,c:'#0d151a'}]})},
 {id:'n5',c:'Nature',n:'Crescent',f:()=>mountain({seed:15,sky:['#16315e','#4a6fa5','#c9a0a8','#e9b99a'],moon:[130,260],crescent:'#4a6fa5',ridges:[{y:.8,amp:80,f:1.6,c:'#1c2430'},{y:.92,amp:60,f:2.6,c:'#12171f'}]})},
 {id:'n6',c:'Nature',n:'Alpenglow',f:()=>mountain({seed:16,sky:['#1d84c8','#7fb8e0','#f0b7c0'],ridges:[{y:.66,amp:190,f:1.2,c:'#b3303d',snow:'#ffd2d6',cap:20},{y:.84,amp:100,f:2,c:'#2a1a2a'}]})},
 {id:'n7',c:'Nature',n:'Golden rock',f:()=>mountain({seed:17,sky:['#25505e','#386a78','#4d8590'],ridges:[{y:.7,amp:180,f:1,c:'#8a4a24',snow:'#f0e8da',cap:18},{y:.88,amp:90,f:2,c:'#10161a'}]})},
 {id:'n8',c:'Nature',n:'Blue ridge',f:()=>mountain({seed:18,sky:['#1c8fb0','#52b6cc','#a6dce0'],ridges:[{y:.75,amp:100,f:1.5,c:'#274a64',snow:'#ffffff',cap:12},{y:.9,amp:70,f:2.6,c:'#0f1c26'}]})},
 /* Modern */
 {id:'m1',c:'Modern',n:'Palm',f:()=>modern({kind:'palm',seed:21,sky:['#1c9bc8','#2aa6d2','#1688b4'],leaf:'#0b3d2e',ox:420,oy:700,a0:180,span:75})},
 {id:'m2',c:'Modern',n:'Balcony',f:()=>modern({kind:'balcony',seed:22,sky:['#2f8aa6','#2a7e9a'],w:'#f3efe6',dk:'#1b1c1e'})},
 {id:'m3',c:'Modern',n:'Palm sky',f:()=>modern({kind:'palm',seed:23,sky:['#8fa3c6','#a9b7d2','#c4cddf'],leaf:'#4a4636',ox:-20,oy:120,a0:-30,span:80})},
 {id:'m4',c:'Modern',n:'Lines',f:()=>modern({kind:'slab',seed:24,sky:['#7fb4ec','#bfdcf8'],a:['#2c3238','#14181c'],line:'#aeb8c2',rot:-18})},
 {id:'m5',c:'Modern',n:'Glass',f:()=>modern({kind:'pyramid',seed:25,sky:['#1ea8e8','#7ed0f4'],a:['#2f86c8','#1a5a98'],b:['#154a82','#0b2e5a']})},
 {id:'m6',c:'Modern',n:'Red tower',f:()=>modern({kind:'tower',seed:26,sky:['#8fb2d8','#bcd2e8','#d7e2ee'],a:['#e53d3a','#a21e22'],b:['#8a1417','#4a0b0e']})},
 {id:'m7',c:'Modern',n:'Teal block',f:()=>modern({kind:'pyramid',seed:27,sky:['#0f5a7a','#1a7898'],a:['#7ac8c0','#3a8a8e'],b:['#1f5a64','#0e3640']})},
 {id:'m8',c:'Modern',n:'Dark edge',f:()=>modern({kind:'slab',seed:28,sky:['#5aa0f0','#9ccaf8'],a:['#1c2026','#080a0c'],line:'#6a7684',rot:-28})}
];
const OLD={w1:'linear-gradient(160deg,#0f2027,#2c5364 45%,#3ddc97)',w2:'linear-gradient(160deg,#ff9966,#ff5e62 50%,#7b2ff7)',w3:'linear-gradient(160deg,#00c6ff,#0072ff 55%,#1b1464)',w4:'linear-gradient(160deg,#134e5e,#71b280)',w5:'linear-gradient(160deg,#f6d365,#fda085 45%,#f093fb)',w6:'linear-gradient(160deg,#0f0c29,#302b63 55%,#24243e)'};
const cache={};
window.wallCss=function(id){
  if(id==='off')return '';
  if(!id||id==='none')id='p0';
  if(OLD[id])return OLD[id];
  if(cache[id])return cache[id];
  const w=WP.find(x=>x.id===id);if(!w)return '';
  return cache[id]=svgUrl(w.f());
};
window.wallLabel=function(id){if(id==='off')return 'None';if(!id||id==='none')id='p0';const w=WP.find(x=>x.id===id);return w?w.n:null};

/* ================= overlays + back button ================= */
let ov={picker:false,preview:null,add:false,edit:false,auth:false};
function ovSync(){try{N.overlay(!!(ov.picker||ov.preview||ov.add||ov.edit||ov.auth))}catch(e){}}
window.closeOverlay=function(){
  if(ov.auth){closeAuth();return}
  if(ov.add){closeAdd();return}
  if(ov.preview){closePreview();return}
  if(ov.picker){closePicker2();return}
  if(ov.edit){exitEdit();return}
  ovSync();
};

/* ================= wallpaper picker ================= */
const pg=document.getElementById('wp-page'),pv=document.getElementById('wp-prev');
function renderPicker(){
  const cur0=(S.settings&&S.settings.wall)||'none',cur=cur0==='none'?'p0':cur0;
  const cats=['Pravah','Tech','Nature','Modern'];
  let h=`<div class="wph"><button class="ib" id="wp-back" aria-label="Back"><svg class="ic"><use href="#back"/></svg></button><h2>Wallpaper</h2></div><div class="wps">`;
  h+=`<div class="wpq"><button class="wpb" id="wp-none">No wallpaper</button><button class="wpb" id="wp-gal">${'My photo / gallery'}</button></div>`;
  cats.forEach(c=>{
    h+=`<div class="wpc">${c}</div><div class="wpg">`+WP.filter(x=>x.c===c).map(x=>`<button class="wpt${cur===x.id?' sel':''}" data-w="${x.id}" aria-label="${x.n}" style="background-image:${window.wallCss(x.id)}"></button>`).join('')+`</div>`;
  });
  h+=`</div>`;
  pg.innerHTML=h;
}
function openPicker(){ov.picker=true;renderPicker();pg.classList.add('on');ovSync()}
function closePicker2(){ov.picker=false;pg.classList.remove('on');ovSync()}
function openPreview(id){
  ov.preview=id;
  const sc=(window.SC||[]).slice(0,8);
  pv.innerHTML=`<div class="pvh"><button class="ib" id="pv-x" aria-label="Close"><svg class="ic"><use href="#close"/></svg></button><h2>Preview</h2></div>
  <div class="pvc" style="background-image:${id==='custom'?`linear-gradient(rgba(0,0,0,.24),rgba(0,0,0,.24)),url(${wallData})`:window.wallCss(id)}">
    <div class="pvl"><img src="logo.png" alt=""></div>
    <div class="pvg">${sc.map(s=>`<div class="pvi"><span>${scIcon(s)}</span><small>${esc(s.n)}</small></div>`).join('')}</div>
    <div class="pvs">Search or type address</div></div>
  <button class="pvb" id="pv-set">Set Wallpaper</button>`;
  pv.classList.add('on');ovSync();
}
function closePreview(){ov.preview=null;pv.classList.remove('on');ovSync()}
pg.onclick=e=>{
  if(e.target.closest('#wp-back'))return closePicker2();
  if(e.target.closest('#wp-none')){N.setSetting('wall','off');return closePicker2()}
  if(e.target.closest('#wp-gal')){N.pickWallpaper();return}
  const t=e.target.closest('.wpt');if(t)openPreview(t.dataset.w);
};
pv.onclick=e=>{
  if(e.target.closest('#pv-x'))return closePreview();
  if(e.target.closest('#pv-set')){const id=ov.preview;N.setSetting('wall',id);closePreview();closePicker2()}
};
document.getElementById('h-wall').onclick=openPicker;
/* settings > Appearance > Wallpaper opens the same picker */
window.openWallPicker=openPicker;

/* ================= shortcuts: free positions, brand icons, round / box ================= */
const BR={
 google:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#fff"/><g transform="translate(6 6) scale(.75)"><path fill="#FFC107" d="M43.6 20.1H42V20H24v8h11.3c-1.6 4.7-6.1 8-11.3 8-6.6 0-12-5.4-12-12s5.4-12 12-12c3.1 0 5.8 1.2 8 3l5.7-5.7C34 6.1 29.3 4 24 4 12.9 4 4 12.9 4 24s8.9 20 20 20 20-8.9 20-20c0-1.3-.1-2.7-.4-3.9z"/><path fill="#FF3D00" d="m6.3 14.7 6.6 4.8C14.7 15.1 19 12 24 12c3.1 0 5.8 1.2 8 3l5.7-5.7C34 6.1 29.3 4 24 4 16.3 4 9.7 8.3 6.3 14.7z"/><path fill="#4CAF50" d="M24 44c5.2 0 9.9-2 13.4-5.2l-6.2-5.2C29.2 35.1 26.7 36 24 36c-5.2 0-9.6-3.3-11.3-7.9l-6.5 5C9.5 39.6 16.2 44 24 44z"/><path fill="#1976D2" d="M43.6 20.1H42V20H24v8h11.3c-.8 2.2-2.200 4.2-4.100 5.600l6.200 5.200C37 39.200 44 34 44 24c0-1.300-.1-2.700-.4-3.900z"/></g></svg>',
 youtube:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#FF0000"/><rect x="8" y="13.5" width="32" height="21" rx="6.5" fill="#fff"/><path d="M20.5 19.5v9l8-4.5z" fill="#FF0000"/></svg>',
 facebook:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#1877F2"/><path fill="#fff" d="M26.5 48V29.5h5.600l.9-6.500h-6.500v-4.200c0-1.900.6-3.200 3.200-3.200h3.400V9.800c-.6-.1-2.600-.3-4.900-.3-4.900 0-8.200 3-8.200 8.400V23H14v6.500h5.900V48z"/></svg>',
 instagram:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="url(#igb)"/><rect x="11.500" y="11.500" width="25" height="25" rx="7.500" fill="none" stroke="#fff" stroke-width="3"/><circle cx="24" cy="24" r="6" fill="none" stroke="#fff" stroke-width="3"/><circle cx="31" cy="17" r="1.900" fill="#fff"/></svg>',
 telegram:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="url(#tgb)"/><path fill="#fff" d="M36.500 12.500 10 22.800c-1.600.7-1.500 1.600-.3 2l6.700 2.100 2.600 8c.3.900.2 1.200 1.100 1.200.7 0 1-.3 1.400-.7l3.300-3.200 6.800 5c1.300.7 2.100.3 2.400-1.200l4.400-20.800c.5-1.900-.7-2.700-1.900-2.200zM17.700 25.800 31.600 17c.7-.4 1.300-.2.800.3L21 27.700l-.5 5.100z"/></svg>',
 x:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#000"/><g transform="translate(10 10) scale(1.2)"><path fill="#fff" d="M18.244 2.250h3.308l-7.227 8.260 8.502 11.240H16.170l-5.214-6.817L4.990 21.750H1.680l7.730-8.835L1.254 2.250H8.080l4.713 6.231zm-1.161 17.520h1.833L7.084 4.126H5.117z"/></g></svg>',
 flipkart:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#2874F0"/><path d="M14 17.500h20l-1.500 20.500a2 2 0 0 1-2 1.800h-13a2 2 0 0 1-2-1.800z" fill="#FFE11B"/><path d="M19 17.500v-2.500a5 5 0 0 1 10 0v2.500" fill="none" stroke="#FFE11B" stroke-width="2.500" stroke-linecap="round"/><path fill="#2874F0" d="M25.600 35.500v-8.200h2.700l.4-3.100h-3.100v-2c0-.9.300-1.500 1.600-1.500h1.700v-2.800c-.3 0-1.300-.1-2.400-.1-2.400 0-4 1.400-4 4.100v2.300h-2.700v3.100h2.700v8.200z"/></svg>',
 amazon:'<svg viewBox="0 0 48 48"><rect width="48" height="48" fill="#232F3E"/><text x="24" y="29" text-anchor="middle" font-family="Arial,Helvetica,sans-serif" font-weight="800" font-size="27" fill="#fff">a</text><path d="M12 34.500c7 4.600 16.500 4.600 24 0" fill="none" stroke="#FF9900" stroke-width="3" stroke-linecap="round"/><path d="M32.500 31.800l4 2.500-1.200-4.700" fill="none" stroke="#FF9900" stroke-width="2.500" stroke-linecap="round" stroke-linejoin="round"/></svg>'
};
const PAL=[['#6C5CE7','#A29BFE'],['#FF6B6B','#FF9F43'],['#00B894','#55EFC4'],['#0984E3','#74B9FF'],['#E84393','#FD79A8'],['#F39C12','#F6D365'],['#2D3436','#636E72'],['#8E44AD','#C56CF0']];
function scIcon(s){
  if(BR[s.k])return `<span class="tl">${BR[s.k]}</span>`;
  const h=[...(s.n||'?')].reduce((a,c)=>a+c.charCodeAt(0),0),p=PAL[h%PAL.length];
  const fav=s.d||host(s.u);
  return `<span class="tl cu" style="--g1:${p[0]};--g2:${p[1]}"><b class="lt">${esc((s.n[0]||'?').toUpperCase())}</b><img src="${favUrl(fav,128)}" alt="" onload="if(this.naturalWidth>16)this.parentNode.classList.add('fav')" onerror="this.remove()"></span>`;
}
const DEF=[
 {k:'google',n:'Google',u:'https://www.google.com',d:'google.com'},
 {k:'youtube',n:'YouTube',u:'https://m.youtube.com',d:'youtube.com'},
 {k:'x',n:'X',u:'https://x.com',d:'x.com'},
 {k:'facebook',n:'Facebook',u:'https://www.facebook.com',d:'facebook.com'},
 {k:'instagram',n:'Instagram',u:'https://www.instagram.com',d:'instagram.com'},
 {k:'telegram',n:'Telegram',u:'https://web.telegram.org/k/',d:'telegram.org'},
 {k:'flipkart',n:'Flipkart',u:'https://www.flipkart.com',d:'flipkart.com'},
 {k:'amazon',n:'Amazon',u:'https://www.amazon.in',d:'amazon.in'}
];
window.SC=DEF.map(x=>Object.assign({},x));let lastRaw=null,drag=null,pressT=0,scroller=0;
const hc=document.getElementById('hc'),layer=document.getElementById('sites'),sarea=document.getElementById('sarea'),hm=document.querySelector('.hmain');
const TW=76,TH=100;
function metrics(){
  const r=hc.getBoundingClientRect(),a=sarea.getBoundingClientRect();
  return{W:r.width,ax:a.left-r.left,ay:a.top-r.top,aw:a.width||r.width-20,r};
}
function defPos(i,m){const cell=m.aw/4,col=i%4,row=Math.floor(i/4);return[m.ax+col*cell+(cell-TW)/2,m.ay+6+row*TH]}
function posOf(s,i,m){return(s.y!=null&&s.xf!=null)?[s.xf*(m.W-TW),s.y]:defPos(i,m)}
function setPos(el,x,y){el.style.transform=`translate(${Math.round(x)}px,${Math.round(y)}px)`;el._x=x;el._y=y}
function layout(skip){
  const m=metrics();
  layer.querySelectorAll('.tile').forEach(t=>{
    if(t===skip)return;
    const i=+t.dataset.i,s=window.SC[i];if(!s)return;
    const p=posOf(s,i,m);setPos(t,p[0],p[1]);
  });
  sarea.style.height=Math.max(112,Math.ceil(window.SC.length/4)*TH+12)+'px';
}
function scRender(){
  layer.innerHTML=window.SC.map((s,i)=>`<div class="tile" data-k="${esc(s.k)}" data-i="${i}">${scIcon(s)}<span class="tn">${esc(s.n)}</span><button class="rm" aria-label="Remove">&times;</button></div>`).join('');
  hc.classList.toggle('edit',ov.edit);
  shapeSync();layout();
}
function saveSC(){const s=JSON.stringify(window.SC.map(({k,n,u,d,xf,y})=>xf==null?{k,n,u,d}:{k,n,u,d,xf:Math.round(xf*10000)/10000,y:Math.round(y)}));lastRaw=s;N.setSetting('shortcuts',s)}
function materialize(){const m=metrics();window.SC.forEach((s,i)=>{if(s.y==null||s.xf==null){const p=defPos(i,m);s.xf=p[0]/(m.W-TW);s.y=p[1]}})}
function shapeSync(){
  const sh=(S.settings&&S.settings.scShape)||'box';hc.dataset.shape=sh;
  const b=document.getElementById('sc-shape');
  if(b)b.innerHTML=sh==='round'?'<svg viewBox="0 0 24 24"><rect x="4" y="4" width="16" height="16" rx="5" fill="none" stroke="currentColor" stroke-width="2.200"/></svg>':'<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="2.200"/></svg>';
}
document.getElementById('sc-shape').onclick=()=>{const sh=((S.settings&&S.settings.scShape)||'box')==='round'?'box':'round';N.setSetting('scShape',sh)};
window.scSync=function(){
  shapeSync();
  const raw=(S.settings&&S.settings.shortcuts)||'';
  if(drag||(raw===lastRaw&&layer.children.length))return;
  lastRaw=raw;
  try{const a=raw?JSON.parse(raw):null;window.SC=(Array.isArray(a)&&a.length)?a.filter(x=>x&&x.u):DEF.map(x=>Object.assign({},x))}catch(e){window.SC=DEF.map(x=>Object.assign({},x))}
  scRender();
};
function enterEdit(){if(ov.edit)return;ov.edit=true;hc.classList.add('edit');ovSync()}
function exitEdit(){ov.edit=false;hc.classList.remove('edit');ovSync()}
document.getElementById('sc-done').onclick=exitEdit;
document.getElementById('sc-reset').onclick=()=>{window.SC.forEach(s=>{delete s.xf;delete s.y});layout();saveSC()};
/* ---- drag anywhere ---- */
let startX=0,startY=0,pid=null,downEl=null,justDragged=false,lastPX=0,lastPY=0;
layer.addEventListener('pointerdown',e=>{
  const t=e.target.closest('.tile');if(!t||e.target.closest('.rm'))return;
  downEl=t;startX=lastPX=e.clientX;startY=lastPY=e.clientY;pid=e.pointerId;
  clearTimeout(pressT);pressT=setTimeout(()=>beginDrag(t),ov.edit?100:320);
});
function beginDrag(t){
  enterEdit();materialize();layout();
  const r=t.getBoundingClientRect();
  drag={el:t,i:+t.dataset.i,gx:startX-r.left,gy:startY-r.top};
  t.classList.add('drag');
  try{t.setPointerCapture(pid)}catch(_){}
  if(navigator.vibrate)try{navigator.vibrate(15)}catch(_){}
  clearInterval(scroller);scroller=setInterval(autoScroll,16);
}
function moveTo(cx,cy){
  const m=metrics();let x=cx-m.r.left-drag.gx,y=cy-m.r.top-drag.gy;
  x=Math.max(0,Math.min(m.W-TW,x));y=Math.max(0,y);
  setPos(drag.el,x,y);
}
function autoScroll(){
  if(!drag)return;const hr=hm.getBoundingClientRect();let d=0;
  if(lastPY<hr.top+60)d=-8;else if(lastPY>hr.bottom-110)d=8;
  if(d){hm.scrollTop+=d;moveTo(lastPX,lastPY)}
}
window.addEventListener('pointermove',e=>{
  lastPX=e.clientX;lastPY=e.clientY;
  if(!drag){if(downEl&&(Math.abs(e.clientX-startX)>9||Math.abs(e.clientY-startY)>9))clearTimeout(pressT);return}
  moveTo(e.clientX,e.clientY);
},{passive:true});
layer.addEventListener('touchmove',e=>{if(drag)e.preventDefault()},{passive:false});
function freeSpot(x,y,self){
  const m=metrics(),others=[...layer.querySelectorAll('.tile')].filter(t=>t!==self);
  const hit=(px,py)=>others.some(t=>Math.abs((t._x||0)-px)<TW*.72&&Math.abs((t._y||0)-py)<TH*.72);
  x=Math.max(0,Math.min(m.W-TW,x));y=Math.max(0,y);
  if(!hit(x,y))return[x,y];
  for(let r=1;r<=8;r++){
    const cand=[];
    for(let dx=-r;dx<=r;dx++)for(let dy=-r;dy<=r;dy++){if(Math.max(Math.abs(dx),Math.abs(dy))!==r)continue;cand.push([x+dx*TW*.75,y+dy*TH*.75])}
    cand.sort((a,b)=>Math.hypot(a[0]-x,a[1]-y)-Math.hypot(b[0]-x,b[1]-y));
    for(const c of cand){const cx=Math.max(0,Math.min(m.W-TW,c[0])),cy=Math.max(0,c[1]);if(!hit(cx,cy))return[cx,cy]}
  }
  return[x,y];
}
function endDrag(){
  clearTimeout(pressT);clearInterval(scroller);
  if(drag){
    const el=drag.el,i=drag.i,m=metrics();
    let x=Math.round(el._x/4)*4,y=Math.round(el._y/4)*4;
    const p=freeSpot(x,y,el);
    const s=window.SC[i];s.xf=p[0]/(m.W-TW);s.y=p[1];
    el.classList.remove('drag');drag=null;justDragged=true;setTimeout(()=>justDragged=false,80);
    setPos(el,p[0],p[1]);saveSC();
  }
  downEl=null;
}
window.addEventListener('pointerup',endDrag);
window.addEventListener('pointercancel',endDrag);
layer.addEventListener('click',e=>{
  if(justDragged)return;
  const rm=e.target.closest('.rm');
  if(rm){const t=rm.closest('.tile');window.SC.splice(+t.dataset.i,1);scRender();saveSC();return}
  const t=e.target.closest('.tile');if(!t||ov.edit)return;
  const s=window.SC[+t.dataset.i];if(s)N.open(s.u);
});
document.getElementById('p-home').addEventListener('click',e=>{
  if(ov.edit&&!e.target.closest('#sites')&&!e.target.closest('.sacts')&&!justDragged)exitEdit();
});
window.addEventListener('resize',()=>layout());
/* ---- add a shortcut ---- */
const ad=document.getElementById('addsc');
function openAdd(){ov.add=true;ad.classList.add('on');document.getElementById('as-n').value='';document.getElementById('as-u').value='';ovSync();setTimeout(()=>document.getElementById('as-n').focus(),80)}
function closeAdd(){ov.add=false;ad.classList.remove('on');document.activeElement&&document.activeElement.blur();ovSync()}
document.getElementById('sc-add').onclick=openAdd;
document.getElementById('as-no').onclick=closeAdd;
document.getElementById('as-yes').onclick=()=>{
  let n=document.getElementById('as-n').value.trim(),u=document.getElementById('as-u').value.trim();
  if(!u)return;
  if(!/^[a-z][a-z0-9+.-]*:\/\//i.test(u))u='https://'+u;
  let h='';try{h=new URL(u).hostname.replace(/^www\./,'')}catch(e){return}
  if(!n)n=h.split('.')[0];n=n.charAt(0).toUpperCase()+n.slice(1);
  const k=Object.keys(BR).find(b=>h.indexOf(b)>=0)||('c'+Date.now().toString(36));
  const item={k,n,u,d:h};
  const m=metrics(),taken=window.SC.map((s,i)=>posOf(s,i,m));
  let spot=null;
  for(let i=0;i<60&&!spot;i++){const p=defPos(i,m);if(!taken.some(t=>Math.abs(t[0]-p[0])<TW*.7&&Math.abs(t[1]-p[1])<TH*.7))spot=p}
  if(spot){item.xf=spot[0]/(m.W-TW);item.y=spot[1]}
  window.SC.push(item);
  closeAdd();scRender();saveSC();
};
ad.addEventListener('click',e=>{if(e.target===ad)closeAdd()});
/* ---- quick-topic chips ---- */
const CATS=[['📰','News','latest news today','#FFF1E0','#B85C00'],['🏏','Cricket','live cricket score','#E6F6EC','#12713B'],['🎬','Movies','new movies','#F1EAFF','#6234C9'],['🌦️','Weather','weather today','#E4F2FF','#1B64B8'],['📱','Recharge','mobile recharge plans','#FFE9EF','#C2185B'],['🗺️','Maps','https://maps.google.com','#E5F7F4','#0F766E'],['🛍️','Shopping','https://www.flipkart.com','#FFF6D6','#9A6700'],['🎵','Music','https://m.youtube.com/results?search_query=new+songs','#FDE8E8','#C0262D']];
const catsEl=document.getElementById('cats');
catsEl.innerHTML=CATS.map((c,i)=>`<button class="cat" data-i="${i}" style="--c:${c[3]};--t:${c[4]}"><i>${c[0]}</i>${c[1]}</button>`).join('');
catsEl.onclick=e=>{const b=e.target.closest('.cat');if(!b)return;const c=CATS[+b.dataset.i];if(/^https?:/.test(c[2]))N.open(c[2]);else N.go(c[2],(S.settings&&S.settings.engine)||'google')};

/* ================= toast ================= */
let toastT=0;
window.pvToast=function(msg){
  let t=document.getElementById('pvtoast');
  if(!t){t=document.createElement('div');t.id='pvtoast';document.body.appendChild(t)}
  t.textContent=msg;t.classList.add('on');clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('on'),2600);
};

/* ================= mini preview of the start page (tabs screen) ================= */
window.miniHome=function(){
  const w=(S.settings&&S.settings.wall)||'none';
  const bg=w==='custom'&&typeof wallData==='string'&&wallData?`linear-gradient(rgba(0,0,0,.24),rgba(0,0,0,.24)),url(${wallData})`:window.wallCss(w);
  const sc=(window.SC||[]).slice(0,8);
  return `<div class="mh${bg?' wp':''}"${bg?` style="background-image:${bg}"`:''}><div class="mh-l"><img src="logo.png" alt=""></div><div class="mh-s"><i></i><span>Search or type a URL</span></div><div class="mh-g">${sc.map(x=>`<span class="mh-i">${scIcon(x)}</span>`).join('')}</div><div class="mh-q"><b></b><b></b><b></b><b></b></div></div>`;
};

/* ================= account: sign up / log in (stored on this device) ================= */
const au=document.getElementById('auth');
let auMode='login',auErr='';
function accList(){try{const a=JSON.parse((S.settings&&S.settings.accounts)||'[]');return Array.isArray(a)?a:[]}catch(e){return[]}}
function normId(v){
  v=(v||'').trim();
  if(v.indexOf('@')>=0){v=v.toLowerCase();return /^[^@\s]+@[^@\s]+\.[^@\s]{2,}$/.test(v)?v:''}
  let d=v.replace(/[\s\-()]/g,'').replace(/^\+?91/,'');
  return /^[6-9]\d{9}$/.test(d)?d:'';
}
function strength(p){let s=0;if(p.length>=6)s++;if(p.length>=9)s++;if(/[A-Z]/.test(p)&&/[a-z]/.test(p))s++;if(/\d/.test(p)&&/[^A-Za-z0-9]/.test(p)||(/\d/.test(p)&&p.length>=8))s++;return Math.min(4,s)}
const SVG_USER='<svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/></svg>';
const SVG_ID='<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M3 8l9 6 9-6"/></svg>';
const SVG_KEY='<svg viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="10" rx="3"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/></svg>';
function fld(id,label,type,ph,icon){
  return `<label class="af"><span>${label}</span><div class="ai">${icon}<input id="${id}" type="${type==='password'?'password':type}" placeholder="${ph}" autocomplete="off" autocapitalize="none" spellcheck="false">${type==='password'?`<button type="button" class="eye" data-eye="${id}">Show</button>`:''}</div></label>`;
}
function renderAuth(){
  const m=auMode;
  const title=m==='signup'?'Create your account':m==='reset'?'Reset password':'Welcome back';
  const sub=m==='signup'?'Sign up to personalise your browser':m==='reset'?'Confirm your details and choose a new password':'Log in to continue with Pravah';
  let f='';
  if(m==='signup')f=fld('au-name','Full name','text','Your name',SVG_USER)+fld('au-id','Mobile number or email','text','10-digit mobile or email',SVG_ID)+fld('au-pw','Password','password','At least 6 characters',SVG_KEY)+'<div class="pwm"><i id="au-st"></i><small id="au-stt"></small></div>'+fld('au-pw2','Confirm password','password','Type the password again',SVG_KEY);
  else if(m==='reset')f=fld('au-id','Mobile number or email','text','Your registered mobile or email',SVG_ID)+fld('au-name','Full name','text','Name used at sign up',SVG_USER)+fld('au-pw','New password','password','At least 6 characters',SVG_KEY);
  else f=fld('au-id','Mobile number or email','text','10-digit mobile or email',SVG_ID)+fld('au-pw','Password','password','Your password',SVG_KEY)+'<div class="forgot"><a id="au-forgot">Forgot password?</a></div>';
  const btn=m==='signup'?'Create account':m==='reset'?'Reset password':'Log in';
  au.innerHTML=`<div class="auh"><button class="ib" id="au-back" aria-label="Back"><svg class="ic"><use href="#back"/></svg></button></div>
  <div class="aub"><div class="aulg"><img src="logo.png" alt=""></div><h1>${title}</h1><p class="sub">${sub}</p>
  ${m==='reset'?'':`<div class="aseg"><button data-m="login" class="${m==='login'?'on':''}">Log in</button><button data-m="signup" class="${m==='signup'?'on':''}">Sign up</button></div>`}
  <div class="aform">${f}<div class="aerr" id="au-err">${auErr}</div><button class="abtn" id="au-go">${btn}</button>
  ${m==='reset'?'<div class="forgot c"><a id="au-tologin">Back to log in</a></div>':''}
  <p class="anote">${SVG_KEY.replace('<svg','<svg class="nk"')} Your account and password stay on this phone. Nothing is sent to any server.</p></div></div>`;
  const pw=document.getElementById('au-pw');
  if(pw&&m==='signup')pw.addEventListener('input',()=>{const sc=strength(pw.value);const b=document.getElementById('au-st');b.style.width=(pw.value?Math.max(sc,1)*25:0)+'%';b.dataset.s=sc;document.getElementById('au-stt').textContent=pw.value?['Too weak','Weak','Okay','Good','Strong'][sc]:''});
  au.querySelectorAll('input').forEach(i=>{i.addEventListener('keydown',e=>{if(e.key==='Enter')doAuth()});i.addEventListener('input',()=>{if(auErr)authErr('')})});
}
function authErr(m){auErr=m;const e=document.getElementById('au-err');if(e)e.textContent=m}
function secure(pw){const salt=N.authSalt(),hash=N.authHash(pw,salt);return(salt&&hash)?{salt,hash}:null}
function doAuth(){
  const v=id=>{const e=document.getElementById(id);return e?e.value:''};
  const L=accList();
  if(auMode==='signup'){
    const name=v('au-name').trim().replace(/\s+/g,' '),id=normId(v('au-id')),pw=v('au-pw'),pw2=v('au-pw2');
    if(name.length<2)return authErr('Please enter your full name');
    if(!id)return authErr('Enter a valid email or 10-digit mobile number');
    if(L.some(a=>a.id===id))return authErr('An account with this '+(id.indexOf('@')>=0?'email':'number')+' already exists. Please log in.');
    if(pw.length<6)return authErr('Password must be at least 6 characters');
    if(pw!==pw2)return authErr('Passwords do not match');
    const sec=secure(pw);if(!sec)return authErr('Could not secure your password. Please try again.');
    L.push({id,name,salt:sec.salt,hash:sec.hash,created:Date.now()});
    N.setSetting('accounts',JSON.stringify(L));N.setSetting('session',id);
    closeAuth();window.pvToast('Welcome to Pravah, '+name.split(' ')[0]+'!');
  }else if(auMode==='reset'){
    const id=normId(v('au-id')),name=v('au-name').trim().replace(/\s+/g,' ').toLowerCase(),pw=v('au-pw');
    const a=L.find(x=>x.id===id);
    if(!id)return authErr('Enter your registered email or mobile number');
    if(!a||a.name.toLowerCase()!==name)return authErr('These details do not match any account');
    if(pw.length<6)return authErr('Password must be at least 6 characters');
    const sec=secure(pw);if(!sec)return authErr('Could not secure your password. Please try again.');
    N.setSetting('accounts',JSON.stringify(L.map(x=>x.id===id?Object.assign({},x,{salt:sec.salt,hash:sec.hash}):x)));
    N.setSetting('session',id);closeAuth();window.pvToast('Password changed. You are logged in.');
  }else{
    const id=normId(v('au-id')),pw=v('au-pw');
    if(!id)return authErr('Enter a valid email or 10-digit mobile number');
    if(!pw)return authErr('Please enter your password');
    const a=L.find(x=>x.id===id);
    if(!a)return authErr('No account found. Tap Sign up to create one.');
    if(N.authHash(pw,a.salt)!==a.hash)return authErr('Wrong password. Try again or reset it.');
    N.setSetting('session',id);closeAuth();window.pvToast('Welcome back, '+a.name.split(' ')[0]+'!');
  }
}
function openAuth(mode){ov.auth=true;auMode=mode==='signup'?'signup':'login';auErr='';renderAuth();au.classList.add('on');ovSync();setTimeout(()=>{const i=document.getElementById(auMode==='signup'?'au-name':'au-id');i&&i.focus()},120)}
function closeAuth(){ov.auth=false;au.classList.remove('on');document.activeElement&&document.activeElement.blur();ovSync()}
window.openAuth=openAuth;
au.addEventListener('click',e=>{
  if(e.target.closest('#au-back'))return closeAuth();
  const sg=e.target.closest('.aseg button');if(sg){auMode=sg.dataset.m;auErr='';renderAuth();return}
  if(e.target.closest('#au-go'))return doAuth();
  if(e.target.closest('#au-forgot')){auMode='reset';auErr='';renderAuth();return}
  if(e.target.closest('#au-tologin')){auMode='login';auErr='';renderAuth();return}
  const eye=e.target.closest('[data-eye]');if(eye){const i=document.getElementById(eye.dataset.eye);const show=i.type==='password';i.type=show?'text':'password';eye.textContent=show?'Hide':'Show'}
});

shapeSync();scRender();
})();

/* ================= status / navigation bar take the wallpaper colour ================= */
(function(){
  let key='';
  const hex=c=>'#'+c.map(v=>('0'+Math.max(0,Math.min(255,Math.round(v))).toString(16)).slice(-2)).join('');
  const lum=c=>(0.299*c[0]+0.587*c[1]+0.114*c[2])/255;
  const send=(t,b)=>{
    const tc=t.map(v=>v*(1-.30)),bc=b.map(v=>v*(1-.38));
    try{N.setBars(hex(tc),hex(bc),lum(tc)<.62,lum(bc)<.62)}catch(e){}
  };
  window.syncBars=function(){
    const w=(S.settings&&S.settings.wall)||'none',on=(S.mode==='home'||S.mode==='about')&&w!=='off';
    const k=on?w+'|'+(w==='custom'?(typeof wallData==='string'?wallData.length:0):0):'off';
    if(k===key)return;key=k;
    if(!on){try{N.setBars('','',false,false)}catch(e){}return}
    if(w==='custom'){
      if(typeof wallData!=='string'||!wallData){key='';return}
      sample(wallData);return;
    }
    const css=window.wallCss(w),m=/url\(['"]?(.*?)['"]?\)\s*$/.exec(css||'');
    if(m){sample(m[1]);return}
    const cols=(css.match(/#[0-9a-fA-F]{6}/g)||[]).map(h=>[1,3,5].map(i=>parseInt(h.substr(i,2),16)));
    if(cols.length)send(cols[0],cols[cols.length-1]);
  };
  function sample(src){
    const im=new Image();
    im.onload=()=>{
      try{
        const W=40,H=80,c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');
        const iw=im.naturalWidth||W,ih=im.naturalHeight||H,sc=Math.max(W/iw,H/ih);
        x.drawImage(im,(W-iw*sc)/2,(H-ih*sc)/2,iw*sc,ih*sc);
        const row=y=>{const d=x.getImageData(0,y,W,1).data;let r=0,g=0,b=0;for(let i=0;i<W;i++){r+=d[i*4];g+=d[i*4+1];b+=d[i*4+2]}return[r/W,g/W,b/W]};
        send(row(1),row(H-2));
      }catch(e){}
    };
    im.src=src;
  }
})();
