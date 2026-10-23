(function(){
 window.__maps = {polygons: [], markers: [], listeners: []};
 class LatLng { constructor(a,b){ if (a instanceof LatLng){this._a=a._a;this._b=a._b;} else if (typeof a==='object'){this._a=a.lat;this._b=a.lng;} else {this._a=a;this._b=b;} } lat(){return this._a} lng(){return this._b} }
 class MVC { constructor(){this._l={}} addListener(n,f){(this._l[n]=this._l[n]||[]).push(f); const h={n,f,o:this}; return h;} _fire(n,e){(this._l[n]||[]).forEach(f=>f(e))} }
 class Map extends MVC { constructor(el,o){super(); this.c=o.center; this.z=o.zoom; window.__maps.map=this;} setCenter(c){this.c=c} setZoom(z){this.z=z} getCenter(){return new LatLng(this.c)} fitBounds(){} panTo(c){this.c=c} }
 class Marker extends MVC { constructor(o){super(); this.o=o||{}; this.vis=true; window.__maps.markers.push(this);} setMap(m){this.o.map=m; if(!m){window.__maps.markers=window.__maps.markers.filter(x=>x!==this)}} setVisible(v){this.vis=v} setPosition(p){this.o.position=p} getPosition(){return new LatLng(this.o.position)} setIcon(){} }
 class Path { constructor(a){this.a=a||[]} getArray(){return this.a.map(p=>p instanceof LatLng?p:new LatLng(p))} }
 class Polygon extends MVC { constructor(o){super(); this.o=o; this.path=new Path(o.paths); window.__maps.polygons.push(this);} setMap(m){this.o.map=m; if(!m){window.__maps.polygons=window.__maps.polygons.filter(x=>x!==this)}} setPath(p){this.path=new Path(p.slice())} getPath(){return this.path} setEditable(e){this.editable=e} setOptions(){} }
 class InfoWindow { constructor(o){this.o=o} open(){} close(){} setContent(){} }
 window.google = {maps: {Map, Marker, Polygon, InfoWindow, LatLng,
   LatLngBounds: class { extend(){} }, Size: class{}, Point: class{}, SymbolPath:{CIRCLE:0},
   DirectionsService: class { route(r,cb){ window.__maps.lastRoute=r; cb({routes:[{waypoint_order:(r.waypoints||[]).map((_,i)=>i)}]}, 'OK'); } },
   DirectionsRenderer: class { setDirections(d){ window.__maps.directions=d; } },
   Geocoder: class { geocode(r,cb){ cb([], 'ZERO_RESULTS'); } },
   places: {Autocomplete: class { bindTo(){} addListener(){} getPlace(){return {}} }},
   TravelMode:{DRIVING:'DRIVING'}, DirectionsStatus:{OK:'OK'},
   event:{removeListener(h){ if(h&&h.o){ h.o._l[h.n]=(h.o._l[h.n]||[]).filter(f=>f!==h.f);} }}}};
 setTimeout(()=>window.iniciarMapa(), 0);
})();
