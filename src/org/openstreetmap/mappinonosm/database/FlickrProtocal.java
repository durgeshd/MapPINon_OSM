/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.openstreetmap.mappinonosm.database;

import com.aetrion.flickr.Flickr;
import com.aetrion.flickr.FlickrException;
import com.aetrion.flickr.people.PeopleInterface;
import com.aetrion.flickr.people.User;
import com.aetrion.flickr.photos.Exif;
import com.aetrion.flickr.photos.Extras;
import com.aetrion.flickr.photos.GeoData;
import com.aetrion.flickr.photos.PhotoList;
import com.aetrion.flickr.photos.Photo;
import com.aetrion.flickr.photosets.Photoset;
import com.aetrion.flickr.photos.PhotosInterface;
import com.aetrion.flickr.photos.SearchParameters;
import com.aetrion.flickr.photos.comments.CommentsInterface;
import com.aetrion.flickr.photosets.PhotosetsInterface;
import com.aetrion.flickr.tags.Tag;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.SAXException;

/**
 *
 * @author nazo
 */
public class FlickrProtocal extends XML {
    private Flickr f=null;
    private PhotosInterface photosi;
    static private HashSet extra = new HashSet();
    static final String base58 = "123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    static{
        extra.add(Extras.DATE_UPLOAD);
        extra.add(Extras.DATE_TAKEN);
        extra.add(Extras.LAST_UPDATE);
        extra.add(Extras.URL_O);
        extra.add(Extras.URL_L);
        extra.add(Extras.MACHINE_TAGS);
        extra.add(Extras.TAGS);
        extra.add(Extras.GEO);
    }

    /** Called only form XMLBase.getInstance();
     * 
     * @param u URI of it. The scheame is "flickr".
     */
    FlickrProtocal(URI u) {
        super(u);
    }

    /** Called only form XMLBase.
     * @param id integer id number 
     */
    FlickrProtocal(int id) {
        super(id);
    }

    /** Called only form XMLBase.
     * @param flickr Flickr object. It holds Flickr API keys.
     */
    void setFlickr(Flickr flickr) {
        this.f=flickr;
    }

    private ArrayList <Photo> serch(){
        String s = uri.getSchemeSpecificPart();
        SearchParameters sp = new SearchParameters();
        PeopleInterface peoplei= f.getPeopleInterface();
        ArrayList<Photo> ret = new ArrayList<Photo>();

        System.out.println("Flickr's API: "+s);
        try {
            if(s.startsWith("tags")){
                link = new URL("http://flic.kr/photos/" + s);
            } else {
                link = new URL("http://flic.kr/" + s);
            }
        } catch(MalformedURLException ex) {
            System.out.println("This is invaild. ");
            return null;
        }
        String[] args = s.split("/");
        title="Flickr photos";
        try { //if the sercg condition is invalld
            if(readDate!=null)sp.setMinUploadDate(readDate);
            for(int i = 0; i < args.length; i++){
                if(args[i].startsWith("tags")){
                    System.out.println("tags: " + args[i + 1]);
                    title+=" tagged &quot;"+args[i+1]+"&quot;.";
                    if(args[i+1].contains(":")){
                        sp.setMachineTags(new String[]{args[i + 1]});
                    }else {
                        sp.setTags(new String[]{args[i + 1]});
                    }
                    break;
                } else if(args[i].startsWith("sets")){
                    PhotosetsInterface photoSeti=f.getPhotosetsInterface();
                    Photoset photoset=photoSeti.getInfo(args[i + 1]);
                    System.out.println("set: " + photoset.getTitle());
                    //sp.????(args[i+1]);
                    title+=" from set &quot;"+photoset.getTitle()+"&quot;.";
                    return photoSeti.getPhotos(args[i + 1], extra, Flickr.PRIVACY_LEVEL_PUBLIC, 60, 1);
                } else {
                    System.out.println("uesrid: " + args[i]);
                    User u = peoplei.getInfo(args[i]);
                    System.out.println("username: " + u.getUsername());
                    title += " of user &quot;" + u.getUsername() + "&quot;, ";
                    sp.setUserId(args[i]);
                }
            }
            sp.setExtras(extra);
            PhotoList pl;
            int page=0;
            do {
                page++;
                pl=photosi.search(sp, 10, page);
                ret.addAll(pl);
            } while(pl.getPages() > page);
            return ret;
        } catch(IOException ex) {
            System.out.println("IO Exception: " + ex.getMessage());
        } catch(SAXException ex) {
            System.out.println("SAX XML Syntax Exception: " + ex.getMessage());
        } catch(FlickrException ex) {
            System.out.println("Flickr has some trable: " + ex.getMessage());
        }
        return ret;
    }

    @Override
    void read() {
        photosi = f.getPhotosInterface();
        ArrayList <Photo> plist = serch();
        System.out.println("\tFound "+plist.size());
        /** analysing Photos */
        for(Photo p:plist){
            photo = new org.openstreetmap.mappinonosm.database.Photo();
            photo.setXML(this);
//                System.out.println("id: " + p.getId());
            setPhotoInfo(p);
            if(photoTable.add(photo) == false){
                org.openstreetmap.mappinonosm.database.Photo oldPhoto = photoTable.get(photo);
                if(oldPhoto.getReadDate().before(photo.getPublishedDate())){
                    photo.setId(oldPhoto.getId());
                    photoTable.remove(oldPhoto);
                    photoTable.add(photo);
                    setExifParameters(p.getId());
                    photo.setReread(true);
                    System.out.println("\tThe JPEG is replaced! photo ID: " + photo.getId());
                } else {
                    oldPhoto.upDate(photo);
                    System.out.println("\tphoto ID: " + oldPhoto.getId());
                }
            } else {
                // This means new photo.
                photo.setNewPhoto(true);
                setExifParameters(p.getId());
                if(photo.getCommentType()!=null){
                    comment(p.getId());
                }
                System.out.println("\tnew photo ID: " + photo.getId());
            }
        }// end of one photo
        System.out.println("Done: Flickr API.");
    }

    private void setPhotoInfo(Photo p) {
        photo.setTitle(entity(p.getTitle()));
        System.out.println("\ttitle: " + p.getTitle());
        photo.setLink(encodeBase58(Long.parseLong(p.getId())));
        System.out.println("\tlink: " + photo.getLink());
        photo.setThumbnale(p.getThumbnailUrl());
        System.out.println("\tthumbnail: " + p.getThumbnailUrl());
        try {
            photo.setOriginal(p.getOriginalUrl());
            System.out.println("\toriginal: " + p.getOriginalUrl());
        } catch(FlickrException ex) {
            System.out.println("\toriginal: not avalable.");
            photo.setLarge(p.getLargeUrl());
        }
        photo.setTakenDate(p.getDateTaken());
        System.out.println("\ttaken:" + p.getDateTaken());
        Date photoPosted = p.getDatePosted();
        photo.setPublishedDate(photoPosted);
        if(readDate==null || photoPosted.after(readDate)){
            readDate = photoPosted;
        }
        System.out.println("\tposted:" + p.getDatePosted());
        photo.setUpdateDate(p.getLastUpdate());
        System.out.println("\tupdated:" + p.getLastUpdate());
        for(Object o2: p.getTags()){
            Tag t = (Tag)o2;
            machineTags(t.getValue());
            System.out.println("\ttag: " + t.getValue());
        }
        /*** get georss information  ***/
        if(photo.getLat() == 0 && photo.getLon() == 0){
            GeoData g = p.getGeoData();
            if(g != null){
                photo.setLat(g.getLatitude());
                photo.setLon(g.getLongitude());
                System.out.println("\tgeorss latlon: " + g.getLatitude() + ", " + g.getLongitude());
            }
        }

        photo.setReadDate(new Date());
    }

    private void setExifParameters(String photoID) {
        String s;
        int latRef=0;
        int lonRef=0;
        int altRef=0;
        int dirRef=0;
        int trackRef=0;
        float speedRef=0;
        ArrayList<Exif> exifal=null;
        try {
            exifal = (ArrayList<Exif>)photosi.getExif(photoID, f.getSharedSecret());
        } catch(IOException ex) {
            System.out.println("EXIF not avalable:" + ex.getMessage());
            return;
        } catch(SAXException ex) {
            System.out.println("EXIF not avalable:" + ex.getMessage());
            return;
        } catch(FlickrException ex) {
            System.out.println("EXIF not avalable:" + ex.getMessage());
            return;
        }

        photo.setDownloadDate(new Date());
        for(Exif e: exifal){
            String tag = e.getTag();
            if(tag.equals("Software")&&e.getRaw().contains("Picasa")){
                photo.setRed();
            } else if (tag.equals("FocalLength")) {
                s=e.getRaw();
                photo.setFocalLength(Float.parseFloat(s.substring(0,s.indexOf(' '))));
                System.out.println("\tfocal length: " + photo.getFocalLength());
            } else if (tag.equals("GPSLatitudeRef")) {
                latRef=(e.getRaw().contains("N"))?1:-1;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSLatitude")) {
                double lat = 0;
                s = e.getRaw();
                System.out.println("\tlatClean: "+s);
                lat = Double.parseDouble(s.substring(0, s.indexOf("deg")-1));
                int i=s.indexOf("'");
                lat += Double.parseDouble(s.substring(s.indexOf("deg")+4, i))/60;
                int j = s.indexOf("\"");
                if(j > 0) {
                    s = s.substring(i + 2, j);
                } else {
                    s = s.substring(i + 2);
                }
                lat += Double.parseDouble(s) / 3600;
                if(latRef != 0){
                    lat = lat*latRef;
                    System.out.println("\tlat: " + lat);
                    photo.setEXIFLat(lat);
                }
            } else if (tag.equals("GPSLongitudeRef")) {
                lonRef=(e.getRaw().contains("E"))?1:-1;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSLongitude")) {
                double lon = 0;
                s = e.getRaw();
                System.out.println("\tlonClean: "+s);
                lon = Double.parseDouble(s.substring(0, s.indexOf("deg")-1));
                int i = s.indexOf("'");
                lon += Double.parseDouble(s.substring(s.indexOf("deg")+4, i))/60;
                int j = s.indexOf("\"");
                if(j > 0) {
                    s = s.substring(i + 2, j);
                } else {
                    s = s.substring(i + 2);
                }
                lon += Double.parseDouble(s) / 3600;
                if(lonRef != 0){
                    lon = lon * lonRef;
                    System.out.println("\tlon: " + lon);
                    photo.setEXIFLon(lon);
                }
            } else if (tag.equals("GPSSpeedRef")) {
                speedRef=(e.getRaw().contains("K"))?1:(e.getRaw().contains("M"))?1.609344F:1.852F;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSSpeed")) {
                if(speedRef!=0){
                    s=e.getRaw();
                    photo.setSpeed(Float.parseFloat(s.substring(0,s.indexOf(" ")))*speedRef);
                    System.out.println("\tspeed: " + photo.getSpeed());
                }
            } else if (tag.equals("GPSAltitudeRef")) {
                altRef=(e.getRaw().contains("Above Sea"))?1:-1;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSAltitude")) {
                if(altRef!=0){
                    s=e.getRaw();
                    photo.setAltitude(Float.parseFloat(s.substring(0,s.indexOf(" ")))*altRef);
                    System.out.println("\talt: " + photo.getAltitude());
                }
            } else if (tag.equals("GPSImgDirectionRef")) {
                dirRef=(e.getRaw().contains("True"))?1:-1;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSImgDirection")) {
                if(dirRef!=0){
                    photo.setDirection(Float.parseFloat(e.getRaw()));
                    System.out.println("\tdir: " + photo.getDirection());
                }
            } else if (tag.equals("GPSTrackRef")) {
                trackRef=(e.getRaw().contains("True"))?1:-1;
            } else if (e.getTagspace().equals("GPS")&&tag.equals("GPSTrack")) {
                if(trackRef!=0){
                    photo.setTrack(Float.parseFloat(e.getRaw()));
                    System.out.println("\ttrack: " + photo.getTrack());
                }
            }
        }
    }

   private String encodeBase58(long num) {
       String ret = "";
       long div;
       while(num >= 58){
           div = num / 58;
           ret = base58.charAt((int)(num - (58 * div))) + ret;
           num = div;
       }
       return "http://flic.kr/p/" + base58.charAt((int)num) + ret;
    }
    private long decodeBase58(String s) {
        long ret=0;
        int i=0;
        char c;
        while( i < s.length()){
            c=s.charAt(i);
            ret+=base58.indexOf(c);
            i++;
            if(i < s.length()){
                ret*=58;
            }
        }
        return ret;
    }
    void reload(org.openstreetmap.mappinonosm.database.Photo pho) {
        this.photo = pho;
        String code = photo.getLink().getPath();

        String flickrID = Long.toString(decodeBase58(code.substring(3)));
        PhotosInterface pi=f.getPhotosInterface();
        try {
            Photo p=pi.getInfo(flickrID, null);
            setPhotoInfo(p);
            setExifParameters(flickrID);
        } catch(IOException ex) {
            Logger.getLogger(FlickrProtocal.class.getName()).log(Level.SEVERE, null, ex);
        } catch(SAXException ex) {
            Logger.getLogger(FlickrProtocal.class.getName()).log(Level.SEVERE, null, ex);
        } catch(FlickrException ex) {
            if(ex.getErrorCode().equals("1")){
                photo.setDeleted(true);
            }
        }
    }
    /**
     * Comennt to the flickr page of the photo. It doesn't work without
     * autherntification.
     * @param id flickr id
     */
    private void comment(String id) {
        CommentsInterface ci = f.getCommentsInterface();
        String url = "http://mappin.hp2.jp/s?" + photo.getShortCode();
        String comment = "";
        String type=photo.getCommentType();
        String lat = org.openstreetmap.mappinonosm.database.Photo.df.format(photo.getLat());
        String lon = org.openstreetmap.mappinonosm.database.Photo.df.format(photo.getLon());
        System.out.println("Comment to ID:" + id);
        if(type.equals("u")){
            comment = url;
        } else if(type.equals("h")){
            comment = "<a href=\"" + url + "\">Veiw it on MapPIN'on OSM</a>";
        } else if(type.equals("h2")){
            comment = "<a href=\"" + url + "\">lat=" + lat + ", lon=" + lon + "</a>";
        } else if(type.equals("i")){
            comment = "<a href=\"" + url + "\"><img src=\"http://tah.openstreetmap.org/MapOf/?lat=" + lat + "'&long=" + lon + "&z=17&w=96&h=96&format=png\" width=\"96\" height=\"96\"/></a>";
        } else if(type.equals("it")){
            comment = "<a href=\"" + url + "\"><img src=\"http://tah.openstreetmap.org/MapOf/?lat=" + lat + "'&long=" + lon + "&z=17&w=96&h=96&format=png\" width=\"96\" height=\"96\"/>Veiw it on MapPIN'on OSM</a>";
        } else {
            comment = "Hi, it got placed on MapPIN'on OSM.";
        }
        try {
            ci.addComment(id, comment);
        } catch(IOException ex) {
            System.out.println("Cannot comment:" + ex.getMessage());
        } catch(SAXException ex) {
            System.out.println("Cannot comment:" + ex.getMessage());
        } catch(FlickrException ex) {
            System.out.println("Cannot comment:" + ex.getErrorMessage());
        }
    }
}
