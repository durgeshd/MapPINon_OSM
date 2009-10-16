/*
 * Copyright 2008, 2009  Xavier Le Bourdon, Christoph Böhme, Mitja Kleider, Shun N. Watanabe
 *
 * This file is derived from a part of 
 * * Openstreetbugs (http://openstreetbugs.schokokeks.org/ ).
 *
 * Openstreetbugs is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with Openstreetbugs.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file implements the client of MapPIN'on OSM (http://mappin.hp2.jp/ ).
 * 
 */
/* configures (always end with '/' ) */
/** base path */
var server_path = "";

/** absolute path */
var img_path = "icons/";

/** relative path */
var data_path ="data/photo";

/** valuables of openlayers' object */
var map = null;
var layer = null;
var permalink = null;
var icons={};

/* caches for MapPIN'on OSM */
var photos = {length:0};/* hash object to recode the loaded photo markers */
var numOfPopuping=0; /** icon markers displaying popups to be limited less than 20. */
var new_point_feature=null;

function init_map(div_id, lon, lat, zoom){
    map = new OpenLayers.Map(div_id, {
        controls: [
            new OpenLayers.Control.Navigation(),
            new OpenLayers.Control.PanZoomBar(),
            new OpenLayers.Control.ScaleLine(),
            new OpenLayers.Control.MousePosition({numDigits:6}),
            new OpenLayers.Control.LayerSwitcher()
        ],
        maxResolution: 156543.0339,
        numZoomLevels: 20,
        units: 'm',
  //      projection: new OpenLayers.Projection("EPSG:900913"),
        displayProjection: new OpenLayers.Projection("EPSG:4326")
    });
    layer = new OpenLayers.Layer.Markers("Photos",{wrapDateLine: true});
    layer.setOpacity(0.7);

    map.addLayers([new OpenLayers.Layer.OSM.Mapnik("Mapnik",{wrapDateLine: true}),
        new OpenLayers.Layer.OSM.CycleMap("CycleMap",{wrapDateLine: true}),
        new OpenLayers.Layer.OSM.Osmarender("Osmarender",{wrapDateLine: true}),
        new OpenLayers.Layer.OSM( "Relief",
            "http://maps-for-free.com/layer/relief/z${z}/row${y}/${z}_${x}-${y}.jpg",
            {numZoomLevels:12,wrapDateLine: true}),
        layer,
        new OpenLayers.Layer.OSM( "Contour",
        "http://www.heywhatsthat.com/bin/contour_tiles.cgi?x=${x}&y=${y}&zoom=${z}&interval=25&color=ff0000",{
            isBaseLayer:false,
            visibility: false,
            wrapDateLine: true            
        })
    ]);

    map.setCenter(new OpenLayers.LonLat(lon, lat).transform(
        new OpenLayers.Projection("EPSG:4326"),
        map.getProjectionObject()),
        zoom);
    document.getElementById("map_OpenLayers_Container").style.cursor = "crosshair";

    icon_size = new OpenLayers.Size(12, 12);
    icon_offset = new OpenLayers.Pixel(-icon_size.w/2, -0.75*icon_size.h);// this is right at the center.
    icons[0]= new OpenLayers.Icon(img_path+'blue.png', icon_size, icon_offset);
    icons[1]= new OpenLayers.Icon(img_path+'yellow.png', icon_size, icon_offset);
    icons[2]= new OpenLayers.Icon(img_path+'red.png', icon_size, icon_offset);
    icons[4]= new OpenLayers.Icon(img_path+'new.png', icon_size, icon_offset);
    map.events.register('moveend', map, refresh);
    map.events.register('click', map, map_click);
    map.addControl(permalink=new OpenLayers.Control.Permalink());
}

function init()
{
  /* get URI param "z" and set zoomlevel */
  var regex = new RegExp("[\\?&]z=([^&#]*)");
  var result = regex.exec(window.location.href);

  zoomlevel = (result == null)?2:result[1];
  navBoxInit();// do it before make the map
  init_map('map', 0, 0, zoomlevel);

  refresh();
}

/* Strip leading and trailing whitespace from str. 
 */
function strip(str){
	return str.replace(/^\s+|\s+$/g, "");
}

/* Save value in a session cookie named "name".
 */
function set_cookie(name, value){
  var expires = (new Date((new Date()).getTime() + 604800000)).toGMTString(); // one week from now
  document.cookie = name+"="+escape(value)+";expires="+expires+";";
}

/* Retrieve the value of cookie "name".
 */
function get_cookie(name){
  if (document.cookie){
    var cookies = document.cookie.split(";");
    for (var i in cookies){
	c = cookies[i].split("=");
	if (strip(c[0]) == name) return unescape(strip(c[1]));
    }
  }
  return null;
}

/* These functions do some coordinate transformations
 */
function y2lat(a) { return 360/Math.PI  * Math.atan(Math.exp(a / 20037508.34 *Math.PI )) - 90; }
function lat2y(a) { return 20037508.34/Math.PI * Math.log(Math.tan(Math.PI*(0.25+a/360))); }
function x2lon(a) { return a * 180 / 20037508.34; }
function lon2x(a) { return a * 20037508.34 / 180; }
function base36(value,digit){
var moduro,i;
var ret="";
for(i=0;i<digit;i++){
 moduro=value%36;
 ret+="0123456789abcdefghijklmnopqrstuvwxyz".substr(moduro,1);
 value=Math.floor(value/36);
}
return ret;
}

/*
 * Html contents of the popups displayed by openstreetbug
 */

/* Html markup for popups showing photo.
 */
function popup_open_photo(photo){
    var i=0;
    var text='<h1>'+photo.title+'</h1>';
    if(photo.link){
        text+='<a target="_blank" title="'+message.title_link+'" href="'+photo.link+'">';
    }
    text+='<img src="'+photo.thumb+'" alt="thumbnail"/>';
    if(photo.link){
        text+='</a>';
    }
    text+='<ul class="description"><li>'+message.lat+': '+photo.lat+'</li>';
    text+='<li>'+message.lon+': '+photo.lon+'</li>';
    if(photo.node){
        for(i=0;i<photo.node.length;i++){
            text+='<li>osm:node=<a href="http://www.openstreetmap.org/browse/node/'+photo.node[i]+'">'+photo.node[i]+'</a></li>';
        }
    }
    if(photo.way){
        for(i=0;i<photo.way.length;i++){
            text+='<li>osm:way=<a href="http://www.openstreetmap.org/browse/way/'+photo.way[i]+'">'+photo.way[i]+'</a></li>';
        }
    }
    text+='</ul><ul>';
    if(photo.link) text+='<li><a target="_blank" title="'+message.title_link+'" href="'+photo.link+'">'+message.action_link+'</a></li>';
    if(photo.original) text+='<li><a target="_blank" title="'+message.title_original+'" href="'+photo.original+'">'+message.action_original+'</a></li>';
    if(photo.rss) text+= '<li><a target="_blank" title="'+message.title_rss+'" href="'+photo.rss+'">'+message.action_rss+'</a></li>';
    if(photo.state!=2)text+= '<li><a target="_blank" title="'+message.title_edit+'" href="http://www.openstreetmap.org/edit?lat='+photo.lat+'&lon='+photo.lon+'&zoom=17">'+message.action_edit+'</a></li></ul>';
    return text;
}


function popup_new_point(lon,lat){
  var text='<h1>'+message['here_is']+'</h1>';
  text+="<ul class=\"description\"><li>"+message.lat+": "+lat.toFixed(6)+"</li><li>"+message.lon+":"+lon.toFixed(6)+"</li></ul>"
            +"<p>"+message['try_to_tag']+"<br/><input size=\"25\" value=\"mappin:at=";
lon=Math.round((lon+180)*1000000);
text+=base36(lon,5);
lon=Math.floor(lon/60466176);
lat=Math.round((lat+90)*1000000)*6+lon;
text+=base36(lat,6);
return text+"\"/></p>";
}

/*
 * AJAX functions
 */

/* Request points from the server.
 */
var last_request={x:null,y:null};
function make_url(x,y){
  if(last_request.x!=x || last_request.y!=y) {
  url = server_path+data_path+((x>0)?'+'+x:'-'+(-x))+((y>0)?'+'+y:'-'+(-y))+".js";
  var script = document.createElement("script");
  script.src = url;
  script.type = "text/javascript";
  document.body.appendChild(script);
  document.getElementById("readingData").innerHTML = url;
  last_request.x=x;last_request.y=y;
  }
}

/* This function is called from the scripts that are returned 
 * on make_url calls.
 */
function AJAXI(photos_i){
  for(id in photos_i){
    if (!photos[id]){
      var photo_i=photos_i[id];
      var photo={
        lat: photo_i.la,
        lon: photo_i.lo,
        title: photo_i.ti,
        thumb: photo_i.th,
        link: photo_i.li,
        original: photo_i.o,
        state: photo_i.s,
        node: photo_i.n,
        way: photo_i.w,
        rss: photo_i.r
      };
      photo.feature = create_feature(photo);
      photos[id]=photo;
      photos.length++;
    }
  }
  document.getElementById("numberOfPhoto").innerHTML = photos.length;
}
/* This function creates a feature and adds a corresponding
 * marker to the map.
 */
function create_feature(photo){
  var feature = new OpenLayers.Feature(layer, new OpenLayers.LonLat(lon2x(photo.lon), lat2y(photo.lat)), {icon: icons[photo.state].clone()});
  feature.popupClass = OpenLayers.Class(OpenLayers.Popup.FramedCloud);
  feature.photo=photo;
  var marker = feature.createMarker();
  marker.events.register("click", feature, marker_click);
  marker.events.register("mouseover", feature, marker_mouseover);
  marker.events.register("mouseout", feature, marker_mouseout);

  layer.addMarker(marker);
  return feature;
}

/****** events called from Openlayers ************/
/** Map events */
function map_click(ev){
    var lonlat=map.getLonLatFromPixel(ev.xy);
    if(!new_point_feature){
        new_point_feature = new OpenLayers.Feature(layer, lonlat, {icon: icons[4]});
        new_point_feature.popupClass = OpenLayers.Class(OpenLayers.Popup.FramedCloud);
    } else {
        new_point_feature.lonlat=map.getLonLatFromPixel(ev.xy);
        if(new_point_feature.popup){
            map.removePopup(new_point_feature.popup);
            new_point_feature.popup.destroy();
        }
        if(new_point_feature.popup){
            layer.removeMarker(new_point_feature.marker);
        }
    }
    layer.addMarker(new_point_feature.createMarker());
    new_point_feature.popup=new new_point_feature.popupClass(new_point_feature.id+'_popup',
        lonlat,
        new_point_feature.data.popupSize,
        popup_new_point(x2lon(lonlat.lon),y2lat(lonlat.lat)),
        new_point_feature.marker.icon,
        true,
        popup_close);
    new_point_feature.popup.feature=new_point_feature;
    map.addPopup(new_point_feature.popup);
    if(!new_point_feature.popuped){
        numOfPopuping++;
        new_point_feature.popuped=true;
    }
    new_point_feature.marker.events.register("click", new_point_feature, marker_click);
    new_point_feature.marker.events.register("mouseover", new_point_feature, marker_mouseover);
    new_point_feature.marker.events.register("mouseout", new_point_feature, marker_mouseout);
    OpenLayers.Event.stop(ev);
}

/* map moveover events */
function refresh(){
  var params = permalink.createParams();
  var zoom = params.zoom;
  var lon = params.lon;
  var lat = params.lat;
  var layers = params.layers;
  
  if (zoom > 10) {
    url=make_url(Math.round(lon*20),Math.round(lat*20));
    document.getElementById("rsslink").style.display = "list-item";
    document.getElementById("rsslink").innerHTML = message.rsslink;
  } else {
    document.getElementById("rsslink").style.display = "none";
  }
  document.getElementById("permalink").innerHTML = "<a href='?lon="+lon+"&lat="+lat+"&zoom="+zoom+"&layers="+layers+"'>"+message.permalink+"</a>";
  document.getElementById("osmlink").innerHTML = '<a href="http://www.openstreetmap.org/?lon='+lon+'&lat='+lat+'&zoom='+zoom+'">'+message.osmlink+'</a>';
  document.getElementById("osblink").innerHTML = '<a href="http://openstreetbugs.schokokeks.org/?lon='+lon+'&lat='+lat+'&zoom='+zoom+'&layers='+layers+'">'+message.osblink+'</a>';
  document.getElementById("geofabrik").innerHTML = "<a href='http://tools.geofabrik.de/map/?lon="+lon+"&lat="+lat+"&zoom="+zoom+"'>"+message.geofabric+"</a>";
}

/** Marker events */
function marker_click(ev){// "this" means feature
    if (this.popuped){
        map.removePopup(this.popup)
        this.popuped=false;
        numOfPopuping--;
    } else if (numOfPopuping<20){
        map.addPopup(this.popup);
        this.popuped=true;
        numOfPopuping++;
    }
    document.getElementById("numberOfPopuping").innerHTML = numOfPopuping;
    OpenLayers.Event.stop(ev);
}

function popup_close(ev){
    this.feature.popuped=false;
    numOfPopuping--;
    map.removePopup(this);
    document.getElementById("numberOfPopuping").innerHTML = numOfPopuping;
    OpenLayers.Event.stop(ev);
}

function marker_mouseover(ev){
  if (!this.popuped){
    if(!this.popup){
        this.data.popupContentHTML = popup_open_photo(this.photo);
//        this.createPopup(ture);
        this.popup = new this.popupClass(this.id+'_popup',
                this.lonlat,
                this.data.popupSize,
                this.data.popupContentHTML,
                this.marker.icon,
                true,
                popup_close);
        this.popup.feature = this;
    }
    map.addPopup(this.popup);
  }
  document.getElementById("map_OpenLayers_Container").style.cursor = "pointer";
  OpenLayers.Event.stop(ev);
}

function marker_mouseout(ev){
  if (!this.popuped){
    map.removePopup(this.popup);
  }
  document.getElementById("map_OpenLayers_Container").style.cursor = "crosshair";
  OpenLayers.Event.stop(ev);
}

// adds show/hide-button to navigation bars
function navBoxInit() {
  // shows and hides content and picture (if available) of navigation bars
  // Parameters:
  //     indexNavigationBar: the index of navigation bar to be toggled
  function toggleNavigationBar(index) {
    var NavToggle = document.getElementById("NavToggle" + index);
    var NavFrame = document.getElementById("NavFrame" + index);
    if (!NavFrame || !NavToggle) {return false;}
    // if shown now
    if (NavToggle.firstChild.data == 'hide') {
      for (var NavChild = NavFrame.firstChild;NavChild != null;NavChild = NavChild.nextSibling) {
          if (NavChild.className == 'NavContent') {
              NavChild.style.display = 'none';
          }
          if (NavChild.className == 'NavToggle') {
              NavChild.firstChild.data = 'show';
          }
      }
    } else if (NavToggle.firstChild.data == 'show') {
      for (var NavChild = NavFrame.firstChild;NavChild != null;NavChild = NavChild.nextSibling){
        if (NavChild.className == 'NavContent') {
          NavChild.style.display = 'block';
        }
        if (NavChild.className == 'NavToggle') {
          NavChild.firstChild.data = 'hide';
        }
      }
    }
  }
 
  function toggleNavigationBarFunction(indexNavigationBar) {
    return function() {
      toggleNavigationBar(indexNavigationBar);
      return false;
    };
  }
 
  var index = 0;
  // iterate over all < div >-elements
  var divs = document.getElementsByTagName("div");
  for (var i=0;  i<divs.length; i++) {
    var NavFrame = divs[i];
    // if found a navigation bar
    if (NavFrame.className == "NavFrame") {
      var NavToggle = document.createElement("a");
      NavToggle.className = 'NavToggle';
      NavToggle.setAttribute('id', 'NavToggle' + index);
      NavToggle.setAttribute('href', '#');
      NavToggle.onclick = toggleNavigationBarFunction(index);
      NavToggle.appendChild(document.createTextNode('hide'));

      // add NavToggle-Button as first div-element
      // in < div class="NavFrame" >
      NavFrame.insertBefore(NavToggle,NavFrame.firstChild);
      NavFrame.setAttribute('id', 'NavFrame' + index);
      index++;
    }
  }
  // hide all
  for(var i=0;i<=index;i++){toggleNavigationBar(i);}
}
