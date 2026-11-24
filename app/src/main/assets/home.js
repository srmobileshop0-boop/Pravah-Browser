/* Pravah home: draggable shortcuts, wallpaper picker, rotating-globe intro.
   Loaded after the main script in ui.html (shares its globals: $, S, N, esc, favUrl, LOGO, host). */
(function(){
'use strict';

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
/* start page: the globe keeps turning (one turn every 10 s) whenever the start page is visible */
(function(){
  const cv=document.getElementById('homeGlobe'),pgh=document.getElementById('p-home');
  if(!cv||!pgh)return;
  glInit(()=>{
    const g=makeGlobe(cv);g.frame(0);const t0=Date.now();
    setInterval(()=>{
      if(document.hidden||!pgh.classList.contains('on')||document.getElementById('intro'))return;
      g.frame((((Date.now()-t0)/10000)%1)*g.Wt);
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

const WP=[
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
  if(!id||id==='none')return '';
  if(OLD[id])return OLD[id];
  if(cache[id])return cache[id];
  const w=WP.find(x=>x.id===id);if(!w)return '';
  return cache[id]=svgUrl(w.f());
};
window.wallLabel=function(id){const w=WP.find(x=>x.id===id);return w?w.n:null};

/* ================= overlays + back button ================= */
let ov={picker:false,preview:null,add:false,edit:false};
function ovSync(){try{N.overlay(!!(ov.picker||ov.preview||ov.add||ov.edit))}catch(e){}}
window.closeOverlay=function(){
  if(ov.add){closeAdd();return}
  if(ov.preview){closePreview();return}
  if(ov.picker){closePicker2();return}
  if(ov.edit){exitEdit();return}
  ovSync();
};

/* ================= wallpaper picker ================= */
const pg=document.getElementById('wp-page'),pv=document.getElementById('wp-prev');
function renderPicker(){
  const cur=(S.settings&&S.settings.wall)||'none';
  const cats=['Tech','Nature','Modern'];
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
  if(e.target.closest('#wp-none')){N.setSetting('wall','none');return closePicker2()}
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

/* ================= shortcuts ================= */
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
const XSVG='<svg viewBox="0 0 24 24"><rect width="24" height="24" rx="5.5" fill="#000"/><g transform="translate(4.3 4.1) scale(.64)"><path fill="#fff" d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></g></svg>';
function scIcon(s){
  const fb=s.k==='x'?XSVG:(LOGO[s.k]||`<b class="ltr">${esc((s.n[0]||'?').toUpperCase())}</b>`);
  const img=s.k==='x'?'':`<img src="${favUrl(s.d||host(s.u),128)}" onerror="this.remove()" alt="">`;
  return `<span class="fb">${fb}</span>${img}`;
}
window.SC=DEF.slice();let lastRaw=null,drag=null,pressT=0;
const box=document.getElementById('sites');
const COLS=4,ROWH=92;
const geo=()=>{const w=box.clientWidth||320;return{w,cw:w/COLS}};
function pos(i){const g=geo();return[(i%COLS)*g.cw,Math.floor(i/COLS)*ROWH]}
function saveSC(){const s=JSON.stringify(window.SC.map(({k,n,u,d})=>({k,n,u,d})));lastRaw=s;N.setSetting('shortcuts',s)}
function scRender(){
  const g=geo();
  const items=window.SC.map((s,i)=>`<div class="tile" data-k="${esc(s.k)}" data-i="${i}" data-id="${esc(s.k+'|'+s.u)}"><span class="tl">${scIcon(s)}</span><span class="tn">${esc(s.n)}</span><button class="rm" aria-label="Remove">&times;</button></div>`).join('');
  const add=`<div class="tile addt" id="sc-add"><span class="tl"><svg class="ic"><use href="#plus"/></svg></span><span class="tn">Add</span></div>`;
  box.innerHTML=items+add;
  box.style.height=Math.ceil((window.SC.length+1)/COLS)*ROWH+'px';
  box.classList.toggle('edit',ov.edit);
  layout();
}
function layout(skip){
  const g=geo();
  box.querySelectorAll('.tile').forEach(t=>{
    t.style.width=g.cw+'px';t.style.height=ROWH+'px';
    if(t===skip)return;
    const i=t.classList.contains('addt')?window.SC.length:+t.dataset.i;const p=pos(i);
    t.style.transform=`translate(${p[0]}px,${p[1]}px)`;
  });
  box.style.height=Math.ceil((window.SC.length+1)/COLS)*ROWH+'px';
}
window.scSync=function(){
  const raw=(S.settings&&S.settings.shortcuts)||'';
  if(drag||raw===lastRaw&&box.children.length)return;
  lastRaw=raw;
  try{const a=raw?JSON.parse(raw):null;if(Array.isArray(a)&&a.length)window.SC=a.filter(x=>x&&x.u);else window.SC=DEF.slice()}catch(e){window.SC=DEF.slice()}
  scRender();
};
function enterEdit(){if(ov.edit)return;ov.edit=true;box.classList.add('edit');ovSync()}
function exitEdit(){ov.edit=false;box.classList.remove('edit');ovSync()}
let startX=0,startY=0,pid=null,downEl=null,moved=false,justDragged=false;
box.addEventListener('pointerdown',e=>{
  const t=e.target.closest('.tile');if(!t||t.classList.contains('addt'))return;
  if(e.target.closest('.rm'))return;
  downEl=t;startX=e.clientX;startY=e.clientY;pid=e.pointerId;moved=false;
  clearTimeout(pressT);
  pressT=setTimeout(()=>beginDrag(t,e),ov.edit?120:320);
});
function beginDrag(t,e){
  enterEdit();
  const i=+t.dataset.i,p=pos(i);
  drag={el:t,i,bx:p[0],by:p[1],sx:startX,sy:startY};
  t.classList.add('drag');
  try{t.setPointerCapture(pid)}catch(_){}
  if(navigator.vibrate)try{navigator.vibrate(15)}catch(_){}
}
window.addEventListener('pointermove',e=>{
  if(!drag){
    if(downEl&&(Math.abs(e.clientX-startX)>9||Math.abs(e.clientY-startY)>9)){clearTimeout(pressT);moved=true}
    return;
  }
  const dx=e.clientX-drag.sx,dy=e.clientY-drag.sy;
  drag.el.style.transform=`translate(${drag.bx+dx}px,${drag.by+dy}px) scale(1.14)`;
  const g=geo(),cx=drag.bx+dx+g.cw/2,cy=drag.by+dy+ROWH/2;
  let col=Math.max(0,Math.min(COLS-1,Math.floor(cx/g.cw))),row=Math.max(0,Math.floor(cy/ROWH));
  let ti=Math.min(window.SC.length-1,row*COLS+col);
  if(ti!==drag.i){
    const it=window.SC.splice(drag.i,1)[0];window.SC.splice(ti,0,it);
    /* re-number tiles */
    const els=[...box.querySelectorAll('.tile:not(.addt)')];
    const order=window.SC.map(s=>s.k+'|'+s.u);
    els.forEach(el=>{el.dataset.i=order.indexOf(el.dataset.id)});
    drag.i=ti;layout(drag.el);
  }
},{passive:true});
box.addEventListener('touchmove',e=>{if(drag)e.preventDefault()},{passive:false});
function endDrag(){
  clearTimeout(pressT);
  if(drag){
    const el=drag.el;el.classList.remove('drag');drag=null;justDragged=true;setTimeout(()=>justDragged=false,60);
    layout();saveSC();
  }
  downEl=null;
}
window.addEventListener('pointerup',endDrag);
window.addEventListener('pointercancel',endDrag);
box.addEventListener('click',e=>{
  if(justDragged)return;
  const rm=e.target.closest('.rm');
  if(rm){const t=rm.closest('.tile');window.SC.splice(+t.dataset.i,1);scRender();saveSC();return}
  if(e.target.closest('#sc-add')){openAdd();return}
  const t=e.target.closest('.tile');if(!t)return;
  if(ov.edit){return}
  const s=window.SC[+t.dataset.i];if(s)N.open(s.u);
});
document.getElementById('p-home').addEventListener('click',e=>{
  if(ov.edit&&!e.target.closest('#sites')&&!justDragged)exitEdit();
});
window.addEventListener('resize',()=>layout());

/* add-shortcut sheet */
const ad=document.getElementById('addsc');
function openAdd(){ov.add=true;ad.classList.add('on');document.getElementById('as-n').value='';document.getElementById('as-u').value='';ovSync();setTimeout(()=>document.getElementById('as-n').focus(),80)}
function closeAdd(){ov.add=false;ad.classList.remove('on');document.activeElement&&document.activeElement.blur();ovSync()}
document.getElementById('as-no').onclick=closeAdd;
document.getElementById('as-yes').onclick=()=>{
  let n=document.getElementById('as-n').value.trim(),u=document.getElementById('as-u').value.trim();
  if(!u)return;
  if(!/^[a-z][a-z0-9+.-]*:\/\//i.test(u))u='https://'+u;
  let h='';try{h=new URL(u).hostname.replace(/^www\./,'')}catch(e){return}
  if(!n)n=h.split('.')[0];n=n.charAt(0).toUpperCase()+n.slice(1);
  window.SC.push({k:'c'+Date.now().toString(36),n,u,d:h});
  closeAdd();scRender();saveSC();
};
ad.addEventListener('click',e=>{if(e.target===ad)closeAdd()});

scRender();
})();
