package com.velvetindex.privateapp;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import android.webkit.*;
import android.graphics.Color;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 WebView web; DB db; ExecutorService work=Executors.newSingleThreadExecutor(); static final int PICK=42;
 void send(String fn,String data){runOnUiThread(()->web.evaluateJavascript("window."+fn+"("+JSONObject.quote(data)+")",null));}
 void browser(String s){try{Uri u=Uri.parse(s);if("https".equals(u.getScheme()))startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}}
 @Override public void onCreate(Bundle b){super.onCreate(b);db=new DB();web=new WebView(this);web.setBackgroundColor(Color.rgb(16,14,22));web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.addJavascriptInterface(new Bridge(),"Native");web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){browser(r.getUrl().toString());return true;}});setContentView(web);web.loadUrl("file:///android_asset/index.html");}
 class Bridge{
 @JavascriptInterface public void list(){work.execute(()->send("onLibrary",db.all().toString()));}
 @JavascriptInterface public void save(String s){work.execute(()->{try{db.save(new JSONObject(s));send("onLibrary",db.all().toString());send("onStatus","Saved");}catch(Exception e){send("onStatus","Save failed");}});}
 @JavascriptInterface public void update(String s){work.execute(()->{try{db.update(new JSONObject(s));send("onLibrary",db.all().toString());}catch(Exception ignored){}});}
 @JavascriptInterface public void delete(int id){work.execute(()->{db.getWritableDatabase().delete("items","id=?",new String[]{""+id});send("onLibrary",db.all().toString());});}
 @JavascriptInterface public void open(String s){runOnUiThread(()->browser(s));}
 @JavascriptInterface public void importMedia(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});}
 @JavascriptInterface public void search(String tags,int page){work.execute(()->{HttpURLConnection c=null;try{String endpoint="https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=60&pid="+Math.max(0,page)+"&tags="+URLEncoder.encode(tags,"UTF-8");c=(HttpURLConnection)new URL(endpoint).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","VelvetIndex/0.1");if(c.getResponseCode()!=200)throw new Exception();ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0&&bytes.size()<2000000)bytes.write(buf,0,n);}Object raw=new JSONTokener(bytes.toString("UTF-8")).nextValue();JSONArray out=new JSONArray();if(raw instanceof JSONArray){JSONArray arr=(JSONArray)raw;for(int j=0;j<arr.length();j++){JSONObject p=arr.optJSONObject(j);if(p==null)continue;String id=p.optString("id");if(!id.matches("[0-9]+"))continue;JSONObject x=new JSONObject();x.put("source","Rule34");x.put("page_url","https://rule34.xxx/index.php?page=post&s=view&id="+id);x.put("file_url",p.optString("file_url"));x.put("preview_url",p.optString("sample_url",p.optString("preview_url")));x.put("title","Post #"+id);x.put("tags",p.optString("tags"));out.put(x);}}send("onSearch",out.toString());}catch(Exception e){send("onSearchError","Source search unavailable.");}finally{if(c!=null)c.disconnect();}});}
 }
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;JSONArray found=new JSONArray();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)pick(found,data.getClipData().getItemAt(j).getUri());}else if(data.getData()!=null)pick(found,data.getData());work.execute(()->{for(int i=0;i<found.length();i++)try{db.save(found.getJSONObject(i));}catch(Exception ignored){}send("onLibrary",db.all().toString());send("onStatus","Files indexed");});}
 void pick(JSONArray arr,Uri uri){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);String name="Imported media";try(Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}JSONObject x=new JSONObject();x.put("source","Local file");x.put("page_url",uri.toString());x.put("file_url",uri.toString());x.put("title",name);x.put("collection","Local imports");arr.put(x);}catch(Exception ignored){}}
 class DB extends SQLiteOpenHelper{
 DB(){super(MainActivity.this,"velvet_private.db",null,1);}
 @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,source TEXT,page_url TEXT UNIQUE,file_url TEXT,preview_url TEXT,title TEXT,tags TEXT,collection TEXT,notes TEXT,created INTEGER)");}
 @Override public void onUpgrade(SQLiteDatabase d,int oldV,int newV){}
 synchronized void save(JSONObject x){ContentValues v=new ContentValues();for(String k:new String[]{"source","page_url","file_url","preview_url","title","tags","collection","notes"})v.put(k,x.optString(k,""));if(x.optString("collection").isEmpty())v.put("collection","Unsorted");v.put("created",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("items",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 synchronized void update(JSONObject x){ContentValues v=new ContentValues();for(String k:new String[]{"title","tags","collection","notes"})v.put(k,x.optString(k,""));getWritableDatabase().update("items",v,"id=?",new String[]{x.optString("id")});}
 synchronized JSONArray all(){JSONArray arr=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,source,page_url,file_url,preview_url,title,tags,collection,notes FROM items ORDER BY created DESC LIMIT 5000",null)){while(c.moveToNext()){JSONObject j=new JSONObject();try{for(int i=0;i<c.getColumnCount();i++)j.put(c.getColumnName(i),c.getString(i));}catch(Exception ignored){}arr.put(j);}}return arr;}
 }
 @Override protected void onDestroy(){web.removeJavascriptInterface("Native");web.destroy();work.shutdown();db.close();super.onDestroy();}
}