const A=require('/workspace/DesiKitchen/mocks/icon/desikitchen-avatar-core.js');
const {Resvg}=require('@resvg/resvg-js');const fs=require('fs');
const OUT='/workspace/DesiKitchen/mocks/icon';
const rig=new A.Rig({auto:false});rig.s.breathe.v=0;rig.s.breathe.t=0;
const fr=rig.frame();
const svg=`<?xml version="1.0" encoding="UTF-8"?>
<!--
  DesiKitchen avatar — vector trace of desikitchen-mark-final.png (IoU 0.976 vs the PNG).
  Coordinates = source PNG pixel space; viewBox is a 600x600 square centred on the mark.
  Rig layers (animate these by id):
    #body      group  — squash / stretch / tilt / hop, anchored at (724,730)
    #hat       path   — white chef hat, house (roof + chimney) is a true cut-out (no mask)
    #groove-l/#groove-r  black pleat strokes on the centre puff
    #eye-l/#eye-r  white arched windows = the eyes (independent, blink/gaze/wink)
  Procedural version (live morph): desikitchen-avatar-core.js → DKAvatar.hatPath()/eyePath().
-->
<svg xmlns="http://www.w3.org/2000/svg" viewBox="${A.VIEWBOX}" width="1024" height="1024">
  <rect id="bg" x="424" y="216" width="600" height="600" fill="#000"/>
  <g id="body">
    <path id="hat" fill="#fff" d="${fr.hat}"/>
    <g id="grooves" fill="none" stroke="#000" stroke-width="${fr.grooveW}" stroke-linecap="round">
      <path id="groove-l" d="${fr.grooves.left}"/>
      <path id="groove-r" d="${fr.grooves.right}"/>
    </g>
    <g id="eyes" fill="#fff">
      <path id="eye-l" d="${fr.eyeL}"/>
      <path id="eye-r" d="${fr.eyeR}"/>
    </g>
  </g>
</svg>
`;
fs.writeFileSync(`${OUT}/desikitchen-avatar.svg`,svg);
fs.writeFileSync(`${OUT}/desikitchen-mark-vector.png`,new Resvg(svg,{fitTo:{mode:'width',value:1024}}).render().asPng());
// transparent foreground variant (no bg rect, grooves stay black) for adaptive-icon work
const fg=svg.replace(/  <rect id="bg"[^\n]*\n/,'');
fs.writeFileSync('/tmp/fg.svg',fg);
console.log('ok');
