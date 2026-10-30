const A=require('/workspace/DesiKitchen/mocks/icon/desikitchen-avatar-core.js');
const {Resvg}=require('@resvg/resvg-js');const fs=require('fs');const {execSync}=require('child_process');
const OUT='/workspace/DesiKitchen/mocks/icon';
const FPS=50, SIZE=512;
const clips={
  idle:{T:6.4,ev:[[0.5,'gaze',-11,3],[1.4,'blink'],[2.2,'gaze',9,-5],[3.4,'gaze',12,4],[4.0,'blink'],[4.38,'blink'],[4.7,'gaze',0,0]]},
  wink:{T:3.2,ev:[[0.45,'state','wink'],[1.35,'state','idle'],[2.5,'blink']]},
  thinking:{T:9.6,ev:[[0.4,'state','thinking'],[2.0,'gaze',4,-14],[3.4,'gaze',12,-11],[4.6,'blink'],[5.0,'gaze',3,-13],[6.4,'gaze',11,-12],[7.5,'state','idle'],[8.4,'blink']]},
};
function sim(name){
  const c=clips[name]; const rig=new A.Rig({auto:false});
  const evs=[...c.ev.map(e=>[e[0],...e.slice(1)]),...c.ev.map(e=>[e[0]+c.T,...e.slice(1)])].sort((a,b)=>a[0]-b[0]);
  const dt=1/FPS, n=Math.round(c.T*FPS); const frames=[]; let ei=0;
  for(let i=0;i<2*n;i++){
    const t=i*dt;
    while(ei<evs.length && evs[ei][0]<=t+1e-9){const e=evs[ei++]; if(e[1]==='gaze')rig.gaze(e[2],e[3]); else if(e[1]==='blink')rig.blink(e[0]); else rig.setState(e[2]);}
    if(i>=n) frames.push(A.toSVG(rig.frame(),{size:SIZE}));
    rig.step(dt);
  }
  return frames;
}
for(const name of process.argv.slice(2)){
  const dir=`/tmp/frames-${name}`; fs.rmSync(dir,{recursive:true,force:true}); fs.mkdirSync(dir);
  const frames=sim(name);
  frames.forEach((s,i)=>fs.writeFileSync(`${dir}/f${String(i).padStart(4,'0')}.png`,new Resvg(s,{fitTo:{mode:'width',value:SIZE}}).render().asPng()));
  const base=`${OUT}/desikitchen-avatar-${name}`;
  execSync(`ffmpeg -y -loglevel error -framerate ${FPS} -i ${dir}/f%04d.png -c:v libvpx-vp9 -pix_fmt yuv420p -b:v 0 -crf 18 -row-mt 1 ${base}.webm`);
  execSync(`ffmpeg -y -loglevel error -framerate ${FPS} -i ${dir}/f%04d.png -vf "fps=25,split[a][b];[a]palettegen=max_colors=32:stats_mode=full:reserve_transparent=0[p];[b][p]paletteuse=dither=none" -loop 0 ${base}.gif`);
  execSync(`ffmpeg -y -loglevel error -framerate ${FPS} -i ${dir}/f%04d.png -c:v libx264 -pix_fmt yuv420p -crf 14 -movflags +faststart ${base}.mp4`);
  console.log(name, frames.length,'frames');
}
