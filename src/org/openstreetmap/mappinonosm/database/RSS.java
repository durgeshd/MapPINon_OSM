/*
 * Copyright (c) 2009, Shun "Nazotoko" Watanabe <nazotoko@gmail.com>
 * All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the OpenStreetMap <www.openstreetmap.org> nor the
 *    names of its contributors may be used to endorse or promote products
 *    derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.openstreetmap.mappinonosm.database;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 * @author nazo
 */
public class RSS extends XML {

    /** for SAX reader */
    private Photo photo = null;
    private Stack <String> stack;
    private String textBuffer;
    private boolean inCDATA = false;
    private String contextEncoded = null;
    private boolean contextCDATA = false;
    private String description = null;
    private boolean descriptionCDATA = false;
    static private SimpleDateFormat rssDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z",Locale.UK);
    static private String[] startHock = {"title","media:thumbnail","media:content"};
    static private String[] endHock = {"link","category"};

    /**
     * @param url URL indicates the file exing there.
     */
    public RSS(URL url) {
        super(url);
    }

    RSS(int id) {
        super(id);
    }
    
    @Override
    public void startDocument() throws SAXException {
        if(pb == null){
            System.err.println("Program's Error: PhotoBase is not set.");
            System.exit(1);
        }
        if(url == null){
            System.err.println("Program's Error: URL of RSS is not set.");
            System.exit(1);
        }
        System.out.println("RSS: "+url);
        stack = new Stack<String>();
    }

    @Override
    public void startElement(String uri, String name, String qualifiedName, Attributes attributes) {
        if(photo==null){
            if (qualifiedName.equals("item")) {
                photo = new Photo();
                photo.setRSS(this);
                System.out.println("<item> start:");
                contextCDATA=false;
                descriptionCDATA=false;
            }
        } else {
            if (qualifiedName.equals("media:thumbnail") && photo.getThumnale() == null) {
                photo.setThumnale(attributes.getValue("url"));
            } else if(qualifiedName.equals("media:content")){
                photo.setOriginal(attributes.getValue("url"));
            }
        }
        textBuffer="";
        stack.push(qualifiedName);
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        textBuffer += new String(ch, start, length);
    }

    @Override
    public void endElement(String uri, String name, String qualifiedName) {
        String fromStack=stack.pop();
        if(photo==null){
            if(qualifiedName.equals("title")){
                title=entity(textBuffer);
                System.out.println("RSS title: " + title);
            }
        } else {
            if(qualifiedName.equals("item")){
                photo.setReadDate(new Date());
                if(photo.original ==null){
                    URL[] urls = getImages(contextEncoded, contextCDATA);
/*                    for(URL urlPhoto: urls){
                        System.out.println("\timage: " + urlPhoto);
                    }*/
                    if(urls.length==1){
                        photo.setOriginal(urls[0]);
                    }
                }
                if(pb.add(photo) == false){
                    Photo oldPhoto = pb.get(photo);
                    if(oldPhoto.getReadDate().compareTo(photo.getUpdateDate()) < 0){
                        photo.setId(oldPhoto.getId());
                        pb.remove(oldPhoto);
                        pb.add(photo);
                        photo.getEXIF();
                        System.out.println("\tThe JPEG is replaced! photo ID: " + photo.getId());
                    } else {
                        oldPhoto.upDate(photo);
                        System.out.println("\tphoto ID: " + oldPhoto.getId());
                    }
                } else {// This means new photo.
                    photo.getEXIF();
                    System.out.println("\tnew photo ID: " + photo.getId());
                }
                photo = null;
            } else if(qualifiedName.equals("pubDate")){
                try {
                    photo.setUpdateDate(rssDateFormat.parse(textBuffer));
                    System.out.println("\tdate:" + photo.getUpdateDate());
                } catch (ParseException ex) {
                    System.out.println("\tfail to parse date! original is :"+textBuffer);
                    photo.setUpdateDate(new Date());
                }
            }else if(qualifiedName.equals("gml:pos")&&stack.search("georss:where")==2){
                String[] st = textBuffer.split("\\s");
                photo.setLat(Double.parseDouble(st[0]));
                photo.setLon(Double.parseDouble(st[1]));
            } else if(qualifiedName.equals("georss:point")){
                String[] st = textBuffer.split("\\s");
                photo.setLat(Double.parseDouble(st[0]));
                photo.setLon(Double.parseDouble(st[1]));
            } else if(qualifiedName.equals("content:encoded")){
                contextEncoded=textBuffer;
//                System.out.println("\tcontext: " + contextEncoded);
            } else if(qualifiedName.equals("title")){
                photo.setTitle(entity(textBuffer));
                System.out.println("\ttitle: " + photo.getTitle());
            } else if(qualifiedName.equals("link")){
                photo.setLink(textBuffer);
                System.out.println("\tlink: " + photo.getLink());
            } else if(qualifiedName.equals("media:keywords")){
                System.out.println("\tmedia:keywords: "+textBuffer);
                String [] demilited=textBuffer.split(", ");
                machineTags(demilited);
            } else if(qualifiedName.equals("media:category")){
                System.out.println("\tmedia:category: "+textBuffer);
                String [] demilited=textBuffer.split(" ");
                machineTags(demilited);
            } else if(qualifiedName.equals("category")){
                System.out.println("\tcategory: "+textBuffer);
                machineTags(textBuffer);
            }
        }
        textBuffer="";
    }
    private String entity(String input) {
        String ret=input.replaceAll("\n", "<br/>");
        ret=ret.replaceAll("\"", "&quot;");
        ret=ret.replaceAll("'", "&apos;");
        return ret;
    }
    private URL [] getImages(String context, boolean cdata){
        ArrayList<URL> urls=new ArrayList<URL>();
        if(!cdata){
            context=context.replaceAll("&lt;","<").replaceAll("&gt;", ">");
        }
        int start=0,end=0;
        while((start=context.indexOf("<img ",end))>0){
            if((end = context.indexOf("src=", start + 5))>0){
                if(context.charAt(end + 4) == '"'){
                    start = end + 5;
                    end = context.indexOf('"',start);
                    String s=context.substring(start,end);
                    try {
                        urls.add(new URL(s));
                    } catch(MalformedURLException ex) {
                        System.out.println("Illigal URL: "+s);
                    }
                }
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }
    private void machineTags(String...  st){
        int in = 0;
        for(String s:st){
            if(s.startsWith("osm:")){
                in = 4;
                if(s.startsWith("node=", in)){
                    in += 5;
                    int node = Integer.parseInt(s.substring(in));
                    photo.addNode(node);
                } else if(s.startsWith("way=", in)){
                    in += 4;
                    int way = Integer.parseInt(s.substring(in));
                    photo.addWay(way);
                }
            }
            if(s.startsWith("mappin:")){
                in = 7;
                if(s.startsWith("at=", in)){
                    in += 3;
                    photo.getMappinAt(s.substring(in));
                }
            }
        }
    }

    @Override
    public void endDocument() throws SAXException {
        System.out.println("RSS Done");
        readDate = new Date();
    }

    @Override
    public void startCDATA(){
        this.inCDATA = true;
        String s;
        if((s=stack.peek()).equals("content:encoded")){
            contextCDATA=true;
        } else if(s.equals("description")){
            descriptionCDATA=true;
        }
    }

    @Override
    public void endCDATA(){
        this.inCDATA = false;
    }

}