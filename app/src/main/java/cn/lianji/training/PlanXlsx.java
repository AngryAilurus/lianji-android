package cn.lianji.training;

import android.util.Xml;
import org.json.*;
import org.xmlpull.v1.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Small, bounded OOXML reader/writer. No formulas or external links are executed. */
public final class PlanXlsx {
    public static final int MAX_FILE=10*1024*1024;
    public static final class Table { public String name; public final List<JSONObject> sessions=new ArrayList<>();public final List<String> errors=new ArrayList<>();public int rows,records; }
    static final class Cell {String value="",formula="";}
    static final class Sheet {String name,path;TreeMap<Integer,TreeMap<Integer,Cell>> rows=new TreeMap<>();}
    final ZipFile zip;final List<String> shared=new ArrayList<>();boolean date1904;
    private PlanXlsx(File file)throws Exception{
        if(file.length()>MAX_FILE)throw new IOException("Excel文件超过10MB，请精简后导入");zip=new ZipFile(file);
    }
    static XmlPullParser parser(InputStream in)throws Exception {XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(in,"UTF-8");return p;}
    InputStream entry(String path)throws Exception{
        ZipEntry e=zip.getEntry(path);if(e==null)throw new IOException("Excel文件缺少 "+path);if(e.getSize()>32*1024*1024L)throw new IOException("Excel工作表过大");
        return new FilterInputStream(zip.getInputStream(e)){int readBytes=0;void count(int n)throws IOException{if(n>0&&(readBytes+=n)>32*1024*1024)throw new IOException("Excel解压内容超过限制");}public int read()throws IOException{int v=super.read();count(v<0?0:1);return v;}public int read(byte[] b,int o,int n)throws IOException{int v=super.read(b,o,n);count(v);return v;}};
    }
    void strings()throws Exception{if(zip.getEntry("xl/sharedStrings.xml")==null)return;try(InputStream in=entry("xl/sharedStrings.xml")){XmlPullParser p=parser(in);StringBuilder item=null;boolean capture=false;for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
        if(event==XmlPullParser.DOCDECL)throw new IOException("不支持包含DTD的工作簿");
        if(event==XmlPullParser.START_TAG){if(p.getName().equals("si"))item=new StringBuilder();if(p.getName().equals("t"))capture=true;}
        else if(event==XmlPullParser.TEXT&&capture&&item!=null)item.append(p.getText());
        else if(event==XmlPullParser.END_TAG){if(p.getName().equals("t"))capture=false;if(p.getName().equals("si")){if(shared.size()>200000)throw new IOException("Excel文本条目过多");shared.add(item.toString());item=null;}}
    }}}
    List<Sheet> sheets()throws Exception{
        Map<String,String> paths=new HashMap<>();try(InputStream in=entry("xl/_rels/workbook.xml.rels")){XmlPullParser p=parser(in);for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next())if(ev==XmlPullParser.START_TAG&&p.getName().equals("Relationship")){
            if("External".equals(p.getAttributeValue(null,"TargetMode")))continue;String target=p.getAttributeValue(null,"Target");if(target==null||target.contains(".."))continue;paths.put(p.getAttributeValue(null,"Id"),target.startsWith("/")?target.substring(1):"xl/"+target);
        }}
        List<Sheet> out=new ArrayList<>();try(InputStream in=entry("xl/workbook.xml")){XmlPullParser p=parser(in);for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next())if(ev==XmlPullParser.START_TAG){
            if(p.getName().equals("workbookPr"))date1904="1".equals(p.getAttributeValue(null,"date1904"))||"true".equals(p.getAttributeValue(null,"date1904"));
            if(p.getName().equals("sheet")){Sheet s=new Sheet();s.name=p.getAttributeValue(null,"name");String id=p.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");s.path=paths.get(id);if(s.path!=null)out.add(s);}
        }}if(out.size()>100)throw new IOException("工作表数量超过100");return out;
    }
    void readSheet(Sheet sheet)throws Exception{try(InputStream in=entry(sheet.path)){XmlPullParser p=parser(in);Cell cell=null;String type="",tag="";int row=0,column=0,count=0;boolean phonetic=false;
        for(int ev=p.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=p.next()){
            if(ev==XmlPullParser.DOCDECL)throw new IOException("不支持包含DTD的工作簿");
            if(ev==XmlPullParser.START_TAG){String name=p.getName();if(name.equals("row")){String r=p.getAttributeValue(null,"r");row=r==null?row+1:Integer.parseInt(r);if(row>50000)throw new IOException("工作表超过50000行");}
                if(name.equals("c")){if(++count>500000)throw new IOException("工作表单元格过多");cell=new Cell();String adr=p.getAttributeValue(null,"r");column=column(adr);type=p.getAttributeValue(null,"t");}
                if(name.equals("rPh"))phonetic=true;if(name.equals("v")||name.equals("f")||name.equals("t"))tag=name;
            }else if(ev==XmlPullParser.TEXT&&cell!=null&&!phonetic){if(tag.equals("v")||tag.equals("t"))cell.value+=p.getText();else if(tag.equals("f"))cell.formula+=p.getText();}
            else if(ev==XmlPullParser.END_TAG){String name=p.getName();if(name.equals("v")||name.equals("t")||name.equals("f"))tag="";if(name.equals("rPh"))phonetic=false;if(name.equals("c")&&cell!=null){if("s".equals(type)&&!cell.value.isEmpty()){int idx=Integer.parseInt(cell.value);if(idx<0||idx>=shared.size())throw new IOException("Excel共享文本索引无效");cell.value=shared.get(idx);}sheet.rows.computeIfAbsent(row,k->new TreeMap<>()).put(column,cell);cell=null;}}
        }
    }}
    static int column(String address){if(address==null)return 0;int c=0;for(char ch:address.toCharArray()){if(ch>='A'&&ch<='Z')c=c*26+ch-'A'+1;else break;}return c-1;}
    static String cell(Map<Integer,Cell> row,int col){Cell c=row==null?null:row.get(col);return c==null?"":c.value.trim();}
    static String norm(String v){return v.replaceAll("[\\s\\u3000()（）:：]","").toLowerCase(Locale.ROOT);}
    static int header(Map<Integer,Cell> row,String... aliases){for(Map.Entry<Integer,Cell> e:row.entrySet())for(String a:aliases)if(norm(e.getValue().value).equals(norm(a)))return e.getKey();return -1;}
    static int integer(String value,int fallback,String label)throws Exception{if(value.isEmpty())return fallback;try{double n=Double.parseDouble(value);if(!Double.isFinite(n)||n!=(int)n)throw new Exception();return (int)n;}catch(Exception e){throw new Exception(label+"需为整数");}}
    String date(String value)throws Exception{
        if(value.isEmpty())throw new Exception("日期为空或公式未计算，请在Excel保存计算结果");String v=value.trim().replace("年","-").replace("月","-").replace("日","").replace('/','-');if(v.matches("\\d{4}\\.\\d{1,2}\\.\\d{1,2}(?:[T ].*)?"))v=v.replace('.','-');
        LocalDate d;
        if(v.matches("\\d{8}"))d=LocalDate.parse(v,DateTimeFormatter.BASIC_ISO_DATE);
        else if(v.matches("\\d+(\\.\\d+)?"))d=(date1904?LocalDate.of(1904,1,1):LocalDate.of(1899,12,30)).plusDays((long)Double.parseDouble(v));
        else{Matcher m=Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[T ].*)?$").matcher(v);if(!m.matches())throw new Exception("日期格式应为2026-10-19或Excel日期");d=LocalDate.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)));}
        if(d.getYear()<1900||d.getYear()>2200)throw new Exception("日期不在1900–2200范围");return d.toString();
    }
    static String actualHeader(Map<Integer,Cell> headers,int set,int field){String[] aliases=field==0?new String[]{set+"组kg",set+"组重量",set+"组公斤"}:field==1?new String[]{set+"组次/秒/米",set+"组次/秒",set+"组次数",set+"组数量"}:new String[]{set+"组RIR"};int col=header(headers,aliases);return String.valueOf(col);}
    Table table(Sheet sheet)throws Exception{
        Table t=new Table();t.name=sheet.name;Map<Integer,Cell> h=null;int start=-1;
        for(Map.Entry<Integer,TreeMap<Integer,Cell>> r:sheet.rows.entrySet()){if(r.getKey()>30)break;if(header(r.getValue(),"动作","动作名称","训练动作")>=0&&header(r.getValue(),"日期","训练日期")>=0&&header(r.getValue(),"组数","计划组数")>=0){h=r.getValue();start=r.getKey();break;}}
        if(h==null)return null;
        int dc=header(h,"日期","训练日期"),nc=header(h,"训练","训练名称","训练日","训练类型"),ec=header(h,"动作","动作名称","训练动作"),sc=header(h,"组数","计划组数"),lc=header(h,"次数下限","下限次/秒/米","次数下限次/秒/米","最低次数"),hc=header(h,"次数上限","上限次/秒/米","次数上限次/秒/米","最高次数"),qc=header(h,"次数","数量","目标次数"),uc=header(h,"单位","记录单位"),rc=header(h,"目标RIR","RIR"),restc=header(h,"休息秒","休息秒数","休息"),loadc=header(h,"重量建议","起始重量","建议重量"),notec=header(h,"备注/下次调整","备注","动作备注"),wc=header(h,"周","周次"),swc=header(h,"游泳分钟","计划泳时分钟"),idc=header(h,"动作编号");
        if(lc<0&&qc<0){t.errors.add("缺少次数列，请填写“次数”或“次数下限、次数上限”，不要使用多栏历史记录表作为计划。");return t;}
        int[][] actualColumns=new int[100][3];for(int j=0;j<100;j++)for(int field=0;field<3;field++)actualColumns[j][field]=Integer.parseInt(actualHeader(h,j+1,field));
        LinkedHashMap<String,JSONObject> grouped=new LinkedHashMap<>();String lastDate="",lastName="";
        for(Map.Entry<Integer,TreeMap<Integer,Cell>> r:sheet.rows.tailMap(start+1).entrySet()){
            Map<Integer,Cell> row=r.getValue();String name=cell(row,ec);if(name.isEmpty()||name.equals("动作")||name.equals("休息"))continue;
            try{
                String rawDate=cell(row,dc);Cell dateCell=row.get(dc);if(rawDate.isEmpty()&&dateCell!=null&&!dateCell.formula.isEmpty())throw new Exception("日期公式未计算，请在Excel中保存计算结果");String d=rawDate.isEmpty()?lastDate:date(rawDate);if(d.isEmpty())throw new Exception("该行缺少日期");lastDate=d;String n=cell(row,nc);if(n.isEmpty())n=lastName.isEmpty()?"导入训练":lastName;lastName=n;
                int sets=integer(cell(row,sc),2,"组数"),low=integer(cell(row,lc),0,"次数下限"),high=integer(cell(row,hc),0,"次数上限");String quantity=cell(row,qc);String unit=cell(row,uc);
                if(unit.isEmpty())unit=name.contains("农夫")||name.contains("行走")?"米":name.contains("平板支撑")?"秒":"次";
                if(!quantity.isEmpty()&&low==0){Matcher m=Pattern.compile("^(\\d+)(?:\\.0)?(?:\\s*[-–—~～至]\\s*(\\d+))?\\s*(次|秒|米)?$").matcher(quantity);if(!m.matches())throw new Exception("次数可填写10或8–12");low=Integer.parseInt(m.group(1));high=m.group(2)==null?low:Integer.parseInt(m.group(2));if(m.group(3)!=null)unit=m.group(3);}
                if(low==0)low=unit.equals("次")?8:20;if(high==0)high=low;
                JSONObject e=new JSONObject().put("name",name).put("sets",sets).put("low",low).put("high",high).put("unit",unit).put("rir",cell(row,rc).replaceFirst("\\.0$","")).put("rest",integer(cell(row,restc),90,"休息秒数")).put("load",cell(row,loadc)).put("note",cell(row,notec)).put("sourceExerciseId",cell(row,idc));PlanOps.validateExercise(e);
                JSONArray actual=new JSONArray();int last=sets;for(int j=1;j<=100;j++){
                    String weight=cell(row,actualColumns[j-1][0]),q=cell(row,actualColumns[j-1][1]),rir=cell(row,actualColumns[j-1][2]);
                    if(!weight.isEmpty()||!q.isEmpty()||!rir.isEmpty())last=Math.max(last,j);
                    JSONObject set=new JSONObject().put("weight",numericText(weight)).put("quantity",numericText(q)).put("rir",numericText(rir)).put("done",!q.isEmpty());actual.put(set);
                }JSONArray trimmed=new JSONArray();for(int j=0;j<last;j++){JSONObject set=actual.getJSONObject(j);trimmed.put(set);if(PlanOps.recorded(set))t.records++;}e.put("actual",trimmed);
                String key=d+"\n"+n;JSONObject session=grouped.get(key);if(session==null){session=new JSONObject().put("date",d).put("name",n).put("week",integer(cell(row,wc),0,"周次")).put("exercises",new JSONArray()).put("swim",new JSONObject().put("planned",cell(row,swc)));grouped.put(key,session);}session.getJSONArray("exercises").put(e);t.rows++;
            }catch(Exception ex){t.errors.add("第"+r.getKey()+"行（"+name+"）："+(ex.getMessage()==null?"格式无效":ex.getMessage()));if(t.errors.size()>=50)break;}
        }
        t.sessions.addAll(grouped.values());LocalDate first=null;for(JSONObject session:t.sessions){LocalDate d=LocalDate.parse(Store.str(session,"date"));if(first==null||d.isBefore(first))first=d;}
        if(first!=null)for(JSONObject session:t.sessions){if(session.optInt("week",0)<=0)session.put("week",(int)((LocalDate.parse(Store.str(session,"date")).toEpochDay()-first.toEpochDay())/7)+1);if(session.optInt("week")>52)t.errors.add("当前计划支持最多52周，请检查日期和周次："+Store.str(session,"date"));}
        return t;
    }
    static String numericText(String value){return value.matches("-?\\d+\\.0+")?value.substring(0,value.indexOf('.')):value;}
    public static List<Table> read(File file)throws Exception{
        PlanXlsx x=new PlanXlsx(file);try{x.strings();List<Table> out=new ArrayList<>();for(Sheet s:x.sheets()){x.readSheet(s);Table table=x.table(s);if(table!=null)out.add(table);}
            if(out.isEmpty())throw new IOException("没有找到逐行训练计划。需包含“日期、动作、组数”表头，可从计划页下载模板。旧版.xls请另存为.xlsx。");return out;
        }finally{x.zip.close();}
    }
    static String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static String columnName(int n){StringBuilder s=new StringBuilder();for(n++;n>0;n=(n-1)/26)s.insert(0,(char)('A'+(n-1)%26));return s.toString();}
    static void zip(ZipOutputStream z,String path,String content)throws IOException{z.putNextEntry(new ZipEntry(path));z.write(content.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
    public static byte[] write(List<JSONObject> sessions,boolean template)throws Exception{
        List<List<Object>> rows=new ArrayList<>();int maxSets=3;for(JSONObject session:sessions){JSONArray es=session.getJSONArray("exercises");for(int i=0;i<es.length();i++)if(!es.getJSONObject(i).optBoolean("archived"))maxSets=Math.max(maxSets,es.getJSONObject(i).getJSONArray("actual").length());}
        List<Object> headers=new ArrayList<>(Arrays.asList("日期","训练","动作","组数","次数下限","次数上限","单位","目标RIR","休息秒","重量建议","备注","周","游泳分钟","动作编号"));
        for(int i=1;i<=maxSets;i++)headers.addAll(Arrays.asList(i+"组kg",i+"组次/秒/米",i+"组RIR"));rows.add(headers);
        if(template){rows.add(new ArrayList<>(Arrays.asList(LocalDate.now().plusDays(1).toString(),"上肢A","杠铃卧推",3,8,10,"次",3,150,"从轻重量试做","每行一个动作",1,15,"")));rows.add(new ArrayList<>(Arrays.asList(LocalDate.now().plusDays(1).toString(),"上肢A","高位下拉",3,10,12,"次",3,120,"按实际余力选择","",1,15,"")));}
        else for(JSONObject session:sessions){if(session.optBoolean("archived"))continue;JSONArray es=session.getJSONArray("exercises");for(int i=0;i<es.length();i++){JSONObject e=es.getJSONObject(i);if(e.optBoolean("archived"))continue;
            List<Object> row=new ArrayList<>(Arrays.asList(Store.str(session,"date"),Store.str(session,"name"),Store.str(e,"name"),e.optInt("sets"),e.optInt("low"),e.optInt("high"),Store.str(e,"unit"),Store.str(e,"rir"),e.optInt("rest"),Store.str(e,"load"),Store.str(e,"note"),session.optInt("week"),Store.str(session.optJSONObject("swim"),"planned"),Store.str(e,"id")));
            JSONArray actual=e.getJSONArray("actual");for(int j=0;j<maxSets;j++){JSONObject set=actual.optJSONObject(j);row.add(Store.str(set,"weight"));row.add(Store.str(set,"quantity"));row.add(Store.str(set,"rir"));}rows.add(row);
        }}
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){
            zip(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            zip(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            zip(z,"xl/workbook.xml","<?xml version=\"1.0\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"训练计划\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            zip(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            StringBuilder sheet=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"2\" width=\"16\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"25\" customWidth=\"1\"/><col min=\"4\" max=\"9\" width=\"13\" customWidth=\"1\"/><col min=\"10\" max=\"11\" width=\"38\" customWidth=\"1\"/></cols><sheetData>");
            for(int r=0;r<rows.size();r++){sheet.append("<row r=\"").append(r+1).append("\">");List<Object> row=rows.get(r);for(int c=0;c<row.size();c++){Object value=row.get(c);if(value==null||value.toString().isEmpty())continue;String adr=columnName(c)+(r+1);if(value instanceof Number)sheet.append("<c r=\"").append(adr).append("\"><v>").append(value).append("</v></c>");else sheet.append("<c r=\"").append(adr).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value.toString())).append("</t></is></c>");}sheet.append("</row>");}sheet.append("</sheetData></worksheet>");zip(z,"xl/worksheets/sheet1.xml",sheet.toString());
        }return out.toByteArray();
    }
}
