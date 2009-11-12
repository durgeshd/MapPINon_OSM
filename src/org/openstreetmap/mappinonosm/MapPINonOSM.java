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

package org.openstreetmap.mappinonosm;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openstreetmap.mappinonosm.database.PhotoTable;
import org.openstreetmap.mappinonosm.database.Photo;
import org.openstreetmap.mappinonosm.database.Tile;
import org.openstreetmap.mappinonosm.database.XMLTable;
import org.openstreetmap.mappinonosm.database.TileTable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.openstreetmap.mappinonosm.database.HistoryTable;
import org.openstreetmap.mappinonosm.database.XML;
import org.openstreetmap.mappinonosm.database.History;

/**
 *
 * @author nazo
 */
public class MapPINonOSM {
    private PhotoTable photoTable;
    private XMLTable xmlTable;
    private HistoryTable hisTable;
    private History history;

    private String domain=null;

    /** Local Directory */
    private File localHtdocsDir=null;

    /** ftp Directory */
    private String ftpHtdocsDir=null;

    /** http Directory */
    private String httpHtdocsDir=null;

    /** relative to domain/httpHtdocsDir */
    private String registration = null;

    /** file path  */
    private File rss_table=null;

    /** file path  */
    private File photo_table=null;

    /** file path */
    private File history_table=null;

    /** directory path relative to htdocs */
    private String backupdir = null;
    /** directory path relative to htdocs */
    private String dataDir=null;

    /** file path relative to htdocs */
    private String rssList=null;

    /** file path relative to htdocs */
    private String historyList=null;

    /** file path relative to htdocs */
    private String historyRSS=null;

    final static private String [] configKeys=new String []{
        "photoTable",
        "RSSTable", 
        "historyTable",
        "RSSList",
        "domain",
        "registrationFile",
        "flickrKey",
        "flickrSecret",
        "backupDir",
        "dataDir",
        "historyList",
        "historyRSS",
        "localHtdocsDir",
        "ftpHtdocsDir",
        "httpHtdocsDir",
    };

    /** initiallizing */
    public MapPINonOSM() {
        InputStream is;
        String line;
        String value;
        String flickrKey = null;
        String flickrSecret = null;
        int i;
        try {
            BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream("./config.txt"),"UTF-8"));
            while((line=br.readLine())!=null){
                for(i = 0; i < configKeys.length; i++){
                    if(line.startsWith(configKeys[i])){
                        value = line.substring(configKeys[i].length() + 1);
                        switch(i){
                            case 0:
                                photo_table = new File(value);
                                break;
                            case 1:
                                rss_table = new File(value);
                                break;
                            case 2:
                                history_table=new File(value);
                                break;
                            case 3:
                                rssList = value;
                                break;
                            case 4:
                                domain = value;
                                break;
                            case 5:
                                registration = value;
                                break;
                            case 6:
                                flickrKey =value;
                                break;
                            case 7:
                                flickrSecret =value;
                                break;
                            case 8:
                                backupdir=value;
                                break;
                            case 9:
                                dataDir=value;
                                break;
                            case 10:
                                historyList=value;
                                break;
                            case 11:
                                historyRSS=value;
                                break;
                            case 12:
                                localHtdocsDir = new File(value);
                                break;
                            case 13:
                                ftpHtdocsDir=value;
                                break;
                            case 14:
                                httpHtdocsDir=value;
                                break;
                        }
                    }
                }
            }
            br.close();
        } catch(FileNotFoundException ex) {
            System.out.println("config file cannot be read.");
        } catch(IOException ex){
            System.out.println("config file cannot be closed.");
        }
        if(localHtdocsDir == null){
            System.err.println("You must set localHtdocsDir at least.");
            System.exit(1);
        }
        hisTable=new HistoryTable();
        history=new History();
        photoTable = new PhotoTable();
        xmlTable = new XMLTable(photoTable);
        if(flickrKey != null && flickrSecret != null){
            xmlTable.setFlickrKeys(flickrKey, flickrSecret);
        }

        if(history_table != null){
            try {
                hisTable.load(is = new GZIPInputStream(new FileInputStream(history_table)));
                is.close();
            } catch(FileNotFoundException ex) {
                System.out.println("History table file '" + history_table.getPath() + "' cannot be read.");
            } catch(IOException ex) {
                System.out.println("Histroy table file '" + history_table.getPath() + "' cannot be closed.");
            }
        } else {
            System.err.println("Warning: History table file is not specified.");
        }
        hisTable.add(history);
        try {
            hisTable.setRoot(new URL("http", domain, httpHtdocsDir));
        } catch(MalformedURLException ex) {
            System.err.println("the URL is illigal: "+ex.getMessage());
        }
        hisTable.setBackupDir(backupdir);
        hisTable.setHistoryList(historyList);
        hisTable.setHistoryRSS(historyRSS);

        if(rss_table==null){
            System.err.println("RSS table file is not specified.");
            return;
        }
        try {
            xmlTable.load(is=new GZIPInputStream(new FileInputStream(rss_table)));
            is.close();
        } catch(FileNotFoundException ex) {
            System.out.println("RSS table file '"+rss_table.getPath()+"' cannot be read.");
        } catch(IOException ex){
            System.out.println("RSS table file '"+rss_table.getPath()+"' cannot be closed.");
        }
    }

    /** photoload */
    public void photoload() {
        InputStream is;
        if(photo_table==null){
            System.err.println("Photo table file is not specified.");
            return;
        }
        try {
            photoTable.load(is = new GZIPInputStream(new FileInputStream(photo_table)), xmlTable);
            is.close();
        } catch(FileNotFoundException ex) {
            System.out.println("Cannot read the Photo base file.");
        } catch(IOException ex) {
            System.out.println("Cannot close the Photo base file.");
        }
    }

    /**
     * 
     * @param urls
     */
    public void addRSS(String... urls){
        /** First stage: adding new RSS */
        XML x;
        for(String u: urls){
            try {
                x = XML.getInstance(new URI(u));
                xmlTable.add(x);
                System.out.println("add: "+u);
            } catch(URISyntaxException ex) {
                System.out.println("Ilreguler URI: " + u);
            }
        }
    }

    /**
     *
     * @param ids
     */
    public void removeRSS(String... ids){
        /** Second stage: removing RSSes */
        int id;
        for(String u: ids){
            id = Integer.parseInt(u);
            xmlTable.remove(id);
            System.out.println("remove: " + u);
        }
    }

    /**
     * getXML from foreground servers registration text file.
     */
    public void getRSS() {
        if(registration == null || domain == null){
            System.err.println("== Get command: not set to use ==");
            return;
        }
        BufferedReader br = null;
        URL url = null;
        String s;
        try {
            url = new URL("http", domain, httpHtdocsDir + registration);
            br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));
            while((s = br.readLine()) != null){
                xmlTable.add(XML.getInstance(new URI(s)));
                System.out.println("got: "+s);
            }
            br.close();
        } catch(MalformedURLException ex){
            System.err.println("Strange URL: "+url); 
        } catch(URISyntaxException ex) {
            System.err.println("URL is strange. Check the config.txt file on the line domain. domain: "+domain+", registration: "+registration);
        } catch(IOException ex) {
            System.err.println("The file cannot to be accessed. URL: "+url);
        }
    }
    
    /** second: reading RSS */
    public void read() {
        history.setNumOfRSS(xmlTable.size());
        xmlTable.read();
    }

    /** making tiles and statistics */
    public void makeTiles(){
        /** third stage: making tiles from photo database*/
        TileTable tb;
        Tile t;
        if(dataDir!=null){
            try {
                tb = new TileTable(new File(localHtdocsDir, dataDir));
                for(Photo p: photoTable){
                    tb.addPhoto(p);
                }
                tb.save();// this will make number of geophoto for each xml.
            } catch(IOException ex) {
                System.out.println("Fail to open tile directory: " + ex.getMessage());
            }
        } else {
            System.out.println("Becase dataDir is not specified, Tiles are not made.");
        }

        /**** making statistics ****/
        try {
            photoTable.toRSS(new FileOutputStream(new File(localHtdocsDir, "newPhoto.rss")), hisTable.getRoot(), history);
        } catch(FileNotFoundException ex) {
            System.out.println("Fail to open rss file: " + ex.getMessage());
        }
        if(rssList!=null){
            OutputStream os;
            try {
                os = new FileOutputStream(new File(localHtdocsDir, rssList));
                history.setNumOfPhoto(xmlTable.toHTML(os));
                os.close();
            } catch(FileNotFoundException ex) {
                System.out.println("Error! Cannot make RSS list HTML.");
            } catch(IOException ex) {
                Logger.getLogger(MapPINonOSM.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    /** save the tables
     *
     */
    public void save(){
        /** final stage: put out tiles **/
        OutputStream os;
        if(rss_table!=null){
            try {
                os = new GZIPOutputStream(new FileOutputStream(rss_table));
                xmlTable.save(os);
                os.close();
            } catch(FileNotFoundException ex) {
                System.out.println("Cannot open rss.json.gz");
            } catch(IOException ex) {
                System.out.println("Cannot close rss.json.gz");
            }
        } else {
            System.err.println("Warning: Photo table is not specified, so RSS table has not been saved.");            
        }

        history.setDate();
        if(history_table != null){
            try {
                os = new GZIPOutputStream(new FileOutputStream(history_table));
                hisTable.save(os);
                os.close();
            } catch(FileNotFoundException ex) {
                System.out.println("Cannot open history.json.gz");
            } catch(IOException ex) {
                System.out.println("Cannot close history.json.gz");
            }
        }
        if(historyList != null){
            try {
                os = new FileOutputStream(new File(localHtdocsDir, historyList));
                hisTable.toHTML(os);
                os.close();
            } catch(FileNotFoundException ex) {
                System.out.println("Cannot open history.json.gz");
            } catch(IOException ex) {
                System.out.println("Cannot close history.json.gz");
            }
        }
        if(historyRSS != null){
            try {
                os = new FileOutputStream(new File(localHtdocsDir, historyRSS));
                hisTable.toRSS(os);
                os.close();
            } catch(FileNotFoundException ex) {
                System.out.println("Cannot open history.json.gz");
            } catch(IOException ex) {
                System.out.println("Cannot close history.json.gz");
            }
        }

        if(photoTable.size() != 0){
            if(photo_table != null){
                try {
                    os = new GZIPOutputStream(new FileOutputStream(photo_table));
                    photoTable.save(os);
                    os.close();
                } catch(FileNotFoundException ex) {
                    System.out.println("Cannot open photo.json.gz");
                } catch(IOException ex) {
                    System.out.println("Cannot close photo.json.gz");
                }
            } else {
                System.err.println("Warning: Photo table is not specified, so photo table has not saved.");
            }

            if(backupdir != null){
                File backupFile = new File(localHtdocsDir, backupdir);
                if(!backupFile.exists()){
                    if(!backupFile.mkdir()){
                        System.out.println("Warning: It cannot make backup directory.");
                        backupFile = null;
                    }
                } else {
                    if(!backupFile.isDirectory()){
                        System.out.println("Warning: The '" + backupFile + "' is not directory.");
                        backupFile = null;
                    }
                }
                backupFile = new File(backupFile,history.getBackupFileName());
                try {
                    os = new GZIPOutputStream(new FileOutputStream(backupFile));
                    photoTable.save(os);
                    os.close();
                } catch(FileNotFoundException ex) {
                    System.out.println("Cannot open backup.");
                } catch(IOException ex) {
                    System.out.println("Cannot close backup.");
                }
            }
        }
    }
    /**
     * show usage and exit
     */
    static public void usage() {
        System.err.println("Background server and database for \"MapPIN'on OSM\" (http://mappin.hp2.jp/).");
        System.err.println("Copyrighted by Nazotoko, 2009, licensed by a BSD-style license.");
        System.err.println("Usage:");
        System.err.println("$ java -cp lib/metafile.jar:MapPIN.jar mappin.Main (command) (args1) (args2) ..");
        System.err.println("");
        System.err.println("commands:");
        System.err.println("\tadd: add RSSes from args on command line. The args are URLs of RSSes.");
        System.err.println("\tget: add RSSes from the registration text file on foreground server.");
        System.err.println("\tread: read the RSSes from the URLs and update databases");
        System.err.println("\ttile: create tiles and rss lists.");
//        System.err.println("\tupload: upload tiles and rss list. arg1:hostname arg2:username arg3:password");
        System.err.println("\tloop: get, read and tile.");
        System.err.println("\tremove: the same as \"loop\" with removing RSS. The args are ID to be removed.");
        System.exit(1);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if(args.length==0){
            usage();
        }
        System.err.println("=========== Loading data tables ===========");
        MapPINonOSM database = new MapPINonOSM();
        if(args[0].equals("remove")){
            System.err.println("=========== removing RSS ===========");
            for(int i = 1; i < args.length; i++){
                database.removeRSS(args[i]);
            }
        }
        database.photoload();// load and remove
        if(args[0].equals("add")){
            for(int i = 1; i < args.length; i++){
                database.addRSS(args[i]);
            }
            database.save();
            System.exit(0);
        }

        if(args[0].equals("get") || args[0].equals("loop") || args[0].equals("remove")){
            System.err.println("=========== Registering RSSes ===========");
            database.getRSS();
        }
        if(args[0].endsWith("read") || args[0].equals("loop") || args[0].equals("remove")){
            System.err.println("=========== Starting to downloading RSSes and photo files ===========");
            database.read();
        }
        if(args[0].endsWith("tile") || args[0].equals("loop") || args[0].equals("remove")){
            System.err.println("=========== Starting to make tiles ===========");
            database.makeTiles();
        }
        if(args[0].endsWith("upload") || args[0].equals("loop") || args[0].equals("remove")){
        }
        System.err.println("=========== Saving data tables ===========");
        database.save();
    }
}
