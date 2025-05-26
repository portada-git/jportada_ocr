package org.elsquatrecaps.portada.portadaocr;


import com.google.api.client.util.Lists;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.documentai.v1beta3.Document;
import com.google.cloud.documentai.v1beta3.Document.Page.Token;
import com.google.cloud.documentai.v1beta3.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1beta3.DocumentProcessorServiceSettings;
import com.google.cloud.documentai.v1beta3.ProcessRequest;
import com.google.cloud.documentai.v1beta3.ProcessResponse;
import com.google.cloud.documentai.v1beta3.RawDocument;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.commons.math3.stat.regression.SimpleRegression;

public class ProcessOcrDocument {
    public static final int ONLY_INDENTED_CRITERIA=0;
    public static final int MULTY_CRITERIA=1;
    public static final int NOT_INDENTED_CRITERIA=2;
    public static final int MIN=0;
    public static final int MAX=1;
    public static final int ORIGINAL_TEXT=0;
    public static final int TEXT_FROM_PARAGRAPHS=1;
    public static final int TEXT_FROM_LINES=2;
    public static final int TEXT_FROM_WORDS=3;
    private static final String CONFIG_FILE_NAME = "project_access.properties";
    private static final Map<String, String> mimeTypes = new HashMap<>();
    private String projectId;
    private String location; // Format is "us" or "eu".
    private String processorId;
    private String credentialsPath=null;
    private InputStream credentialsStream=null;
    private String filePath;
    private byte[] imageFileData;
    private Document documentResponse;
    private int methodForBuildingParagraphsFromLines = 0;
    static {
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("tif", "image/tiff");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("gif", "image/gif");
    }

    public ProcessOcrDocument() {
        
    }
    
    
    public void init(String basePath, String team, InputStream credentials) throws FileNotFoundException, IOException {
        StringBuilder strb = new StringBuilder(basePath);
        Properties prop = new Properties();
        if(!basePath.endsWith("/")){
            strb.append("/");
        }
        strb.append(team);
        if(!team.endsWith("/")){
            strb.append("/");
        }
        strb.append(CONFIG_FILE_NAME);
        prop.load(new FileInputStream(strb.toString()));
        this.projectId = prop.getProperty("projectId");
        this.location = prop.getProperty("location");
        this.processorId = prop.getProperty("processorId");
        this.credentialsPath = null;
        this.credentialsStream = credentials;
    }
    
    public void init(String basePath, String team) throws FileNotFoundException, IOException {
        StringBuilder strb = new StringBuilder(basePath);
        Properties prop = new Properties();
        if(!basePath.endsWith("/")){
            strb.append("/");
        }
        strb.append(team);
        if(!team.endsWith("/")){
            strb.append("/");
        }
        strb.append(CONFIG_FILE_NAME);
        prop.load(new FileInputStream(strb.toString()));
        this.projectId = prop.getProperty("projectId");
        this.location = prop.getProperty("location");
        this.processorId = prop.getProperty("processorId");
        this.credentialsPath = prop.getProperty("credentialsPath");
    }
    
    public String getCredentialsPath(){
        return credentialsPath;
    }
    
    public InputStream getCredentialsStream() throws FileNotFoundException{
        if(credentialsPath!=null){
            updateCredentialsStream();
        }
        return credentialsStream;
    }
    
    public void updateCredentialsStream() throws FileNotFoundException{
        this.credentialsStream = new FileInputStream(new File(credentialsPath).getAbsoluteFile());
    }
    
    public void setCredentialsStream(InputStream is){
        this.credentialsStream = is;
        this.credentialsPath=null;
    }

    public void setFilePath(String path) throws IOException{
        this.filePath = path;
        this.imageFileData = Files.readAllBytes(Paths.get(filePath));
    }
    
    public String getFilePath(){
        return filePath;
    }
    
    public void readImage(String imagePath) throws IOException{
        this.setFilePath(imagePath);
    }
    
    public void process(String imagePath) throws FileNotFoundException, IOException{
        readImage(imagePath);
        process();
    }
    
    public void process() throws FileNotFoundException, IOException{
        String extension = filePath.substring(filePath.lastIndexOf(".")+1);
        String mime = mimeTypes.get(extension);
        String endpoint = String.format("%s-documentai.googleapis.com:443", location);
        GoogleCredentials credentialsProvider = GoogleCredentials.fromStream(getCredentialsStream())
                .createScoped(Lists.newArrayList(Collections.singleton("https://www.googleapis.com/auth/cloud-platform")));
        DocumentProcessorServiceSettings settings =DocumentProcessorServiceSettings.newBuilder().setEndpoint(endpoint)
                .setCredentialsProvider(FixedCredentialsProvider.create(credentialsProvider)).build();
        try (DocumentProcessorServiceClient client = DocumentProcessorServiceClient.create(settings)) {
            String name = String.format("projects/%s/locations/%s/processors/%s", projectId, location, processorId);

            ByteString content = ByteString.copyFrom(imageFileData);

            RawDocument document = RawDocument.newBuilder().setContent(content)
                    .setMimeType(mime).build();

            ProcessRequest request = ProcessRequest.newBuilder().setName(name).setRawDocument(document).build();

            ProcessResponse result = client.processDocument(request);
            documentResponse = result.getDocument();
        }
    }
    
    public String getJsonString() throws InvalidProtocolBufferException{
        return JsonFormat.printer().print(documentResponse);
    }

    public Document getResult(){
        return documentResponse;
    }
    
    public void saveRawText(String path) throws IOException{
        try(FileWriter fw = new FileWriter(path)){
            fw.append(getText(ORIGINAL_TEXT));
        }
    }
    
    public void saveText(String path) throws IOException{
        
        String text = getWordsToString();
        try(FileWriter fw = new FileWriter(path)){
//            fw.append(getParagraphs(0));       
            fw.append(text);
        }
    }
    
    public String getText(){
        return getText(TEXT_FROM_WORDS);
    }
    
    public String getText(int textForm){
        String ret;
        switch (textForm) {
            case ORIGINAL_TEXT:
                ret = documentResponse.getText();
                break;
            case TEXT_FROM_PARAGRAPHS:
                ret = getParagraphs();
                break;
            case TEXT_FROM_LINES:
                ret = getLines();
                break;
            case TEXT_FROM_WORDS:
                ret = getWordsToString();
                break;
            default:
                throw new AssertionError();
        }
        return ret;
    }
    
    public String getWordsToString(){    
        List<Line> lines = new LinkedList<>();
        WordPointer wp = new WordPointer(documentResponse);
        int[] minMaxX = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        while(wp.hasNext()){
            Word w = wp.getNext();
            lines.add(new Line(w));
            if(w.xCenterLeft < minMaxX[MIN]){
                minMaxX[MIN] = w.xCenterLeft;
            }
            if(w.xCenterRight > minMaxX[MAX]){
                minMaxX[MAX] = w.xCenterRight;
            }
        }
        
        for(int thx=2; thx<=150; thx+=2){
            boolean joinLines = true;
            while(joinLines){
                boolean foundCandidates = false;
                for(int i=0; i<lines.size(); i++){
                    for(int j=lines.size()-1; j>i; j--){
                        if(lines.get(i).addWord(lines.get(j),thx)){
                            foundCandidates = true;
                            lines.remove(j);
                        }
                    }            
                }
                joinLines=foundCandidates;
            }
        }
        //ASIGNAR bestLineForRegression
        int xMid = (minMaxX[MAX]-minMaxX[MIN])/2;

        for(Line l1: lines){
            for(Line l2: lines){
                //Actualització de les linies de regressió
                if(l1.xCenterRight-l1.xCenterLeft<0.9*(minMaxX[MAX]-minMaxX[MIN])){
                    //DL2L1 = distància de l2 a l1
                    int distL2ToL1 = Math.abs(l2.predictYFromRegression(xMid) - l1.predictYFromRegression(xMid));
                    //DBRL1L1 = distancia de l1.bestLineForRegression a l1
                    int distBestRegL1ToL1 = Math.abs(l1.bestLineForRegression.predictYFromRegression(xMid) - l1.predictYFromRegression(xMid));
                    //SI AMPLADA(DL2L1)>AMPLADA(DBRL1L1) && AMPLADA(DBRL1L1)<0.9*(minMaxX[MAX]-minMaxX[MIN]
                      // || AMPLADA(DBRL1L1)>=0.9*(minMaxX[MAX]-minMaxX[MIN] && DL2L1 < DBRL1L1
                    if((l2.xCenterRight-l2.xCenterLeft 
                                    > l1.bestLineForRegression.xCenterRight-l1.bestLineForRegression.xCenterLeft
                                && l1.bestLineForRegression.xCenterRight-l1.bestLineForRegression.xCenterLeft 
                                    < 0.9*(minMaxX[MAX]-minMaxX[MIN]))
                            || (l1.bestLineForRegression.xCenterRight-l1.bestLineForRegression.xCenterLeft
                                    >= 0.9*(minMaxX[MAX]-minMaxX[MIN]) 
                                && distL2ToL1 < distBestRegL1ToL1)){
                        l1.setBestLineForRegression(l2);
                    }           
                }else if(l2.xCenterRight-l2.xCenterLeft<0.9*(minMaxX[MAX]-minMaxX[MIN])){
                    //DL2L1 = distància de l2 a l1
                    int distL1ToL2 = Math.abs(l1.predictYFromRegression(xMid) - l2.predictYFromRegression(xMid));
                    //DBRL2L2 = distancia de l2.bestLineForRegression a l2
                    int distBestRegL2ToL2 = Math.abs(l2.bestLineForRegression.predictYFromRegression(xMid) - l2.predictYFromRegression(xMid));
                    //SI AMPLADA(DL1L2)>AMPLADA(DBRL2L2) && AMPLADA(DBRL2L2)<0.9*(minMaxX[MAX]-minMaxX[MIN]
                      // || AMPLADA(DBRL2L2)>=0.9*(minMaxX[MAX]-minMaxX[MIN] && DL1L2 < DBRL2L2
                    if((l1.xCenterRight-l1.xCenterLeft 
                                    > l2.bestLineForRegression.xCenterRight-l2.bestLineForRegression.xCenterLeft
                                && l2.bestLineForRegression.xCenterRight-l2.bestLineForRegression.xCenterLeft 
                                    < 0.9*(minMaxX[MAX]-minMaxX[MIN]))
                            || (l2.bestLineForRegression.xCenterRight-l2.bestLineForRegression.xCenterLeft
                                    >= 0.9*(minMaxX[MAX]-minMaxX[MIN]) 
                                && distL1ToL2 < distBestRegL2ToL2)){
                        l2.setBestLineForRegression(l1);
                    }           
                }        
            }
        }
                
        for(int i=0; i<1; i++){
            Collections.sort(lines, (Line l1, Line l2) -> {
                int y1;
                int y2;
                double p;
                y1 = l1.predictYFromRegression(l1.xCenterLeft);
                y2 = l2.predictYFromRegression(l1.xCenterLeft);
                return y1-y2;
            });
        }
        String ret="";
        if(methodForBuildingParagraphsFromLines==ONLY_INDENTED_CRITERIA){
            ret = buildParagraphsFromLineIndentedOnly(lines, minMaxX);
        }else if(methodForBuildingParagraphsFromLines==MULTY_CRITERIA){
            ret = buildParagraphsFromLineMultiCond(lines, minMaxX);
        }
        
        return ret;
    }

    private String buildParagraphsFromLineIndentedOnly(List<Line> lines, int[] minMaxX){
        Pattern textEndingInAHyphen = Pattern.compile("^.*[-¬]\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        StringBuilder strb = new StringBuilder();
        for(int pos=0; pos<lines.size()-1; pos++){
            String cat=" ";
            if(textEndingInAHyphen.matcher(lines.get(pos).toString()).matches()){
                cat = "";
            }else if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                cat = "\n";
            }
            strb.append(lines.get(pos).text.replaceFirst("-?\n?$", "").replaceAll("\n", " "));
            strb.append(cat);
        }
        if(!lines.isEmpty()){
            strb.append(lines.get(lines.size()-1).text.replace("-?\n?$", "").replaceAll("\n", " "));
            strb.append("\n");
        }        
        return strb.toString();
    }

    private String buildParagraphsFromLineMultiCond(List<Line> lines, int[] minMaxX){
        StringBuilder strb = new StringBuilder();
        Pattern textWithoutEndPoint = Pattern.compile("^.*\\w\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textStartingInLowerCase = Pattern.compile("^\\s*[^A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textEndingInAComma = Pattern.compile("^.*\\w[,;]+\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textEndingInAPeriod = Pattern.compile("^.*\\w\\.+\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textEndingInWordAndAPeriod = Pattern.compile("^.*\\w{3,}\\.+\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textStartingInUppercase = Pattern.compile("^\\s*[A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textAsTitle = Pattern.compile("^\\s*[0-9A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ \\W]{4,}\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textEndingInAHyphen = Pattern.compile("^.*[-¬]\n?$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textAsPageNumOrTitle = Pattern.compile("^\\s*(?:(?:\\d+)|(?:[A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ \\W]*))\\n.*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        Pattern textEndsWithProperName = Pattern.compile("^\\s*.*(?:(?:[A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ]\\.)|(?:[A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ]\\w+(?: y)?))\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
        for(int pos=0; pos<lines.size()-1; pos++){
            String cat="";
            if(textWithoutEndPoint.matcher(lines.get(pos).toString()).matches() && textStartingInLowerCase.matcher(lines.get(pos+1).toString()).matches()){
                if((pos==0 || cat.equals("\n")) && textAsPageNumOrTitle.matcher(lines.get(pos).toString()).matches()){
                    cat="\n";
                }else{
                    cat = " ";
                }
            }else if(textAsTitle.matcher(lines.get(pos).toString()).matches() || textAsTitle.matcher(lines.get(pos+1).toString()).matches()){
                cat = "\n";
            }else if(textEndingInAHyphen.matcher(lines.get(pos).toString()).matches()){
                cat = "";            
            }else if(textEndsWithProperName.matcher(lines.get(pos).toString()).matches() && textStartingInUppercase.matcher(lines.get(pos+1).toString()).matches()){
//                if(Math.abs(lines.get(pos+1).xCenterLeft-minMaxX[MIN])> 24
//                        || lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN] * 1.4, 10)
//                        || ((lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN], 10))
//                        &&  lines.get(pos+1).xCenterRight-lines.get(pos+1).xCenterLeft>0.3*(minMaxX[MAX]-minMaxX[MIN]))){
//                    cat = "\n";
//                }else{
//                    cat = " ";
//                }
                if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                    cat = "\n";
                }else{
                    cat = " ";
                }               
            }else if(textEndingInWordAndAPeriod.matcher(lines.get(pos).toString()).matches() && textStartingInUppercase.matcher(lines.get(pos+1).toString()).matches()){
//                if(Math.abs(lines.get(pos+1).xCenterLeft-minMaxX[MIN])> 20
//                        || lines.get(pos).xCenterRight-lines.get(pos).xCenterLeft<=0.9*(minMaxX[MAX]-minMaxX[MIN])
//                        || lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN],10)){
//                    cat = "\n";                    
//                }else{
//                    cat = " ";
//                }
                if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                    cat = "\n";
                }else{
                    cat = " ";
                }    
            }else if(textStartingInUppercase.matcher(lines.get(pos+1).toString()).matches()){
//                if(lines.get(pos).xCenterRight-lines.get(pos).xCenterLeft<=0.80*(minMaxX[MAX]-minMaxX[MIN])
////                        || lines.get(pos+1).xCenterLeft - (lines.get(pos).heightSum/lines.size())*1.75 > minMaxX[MIN]){
//                        || lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN] * 1.4, 10)
//                        || ((lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN], 10))
//                        &&  lines.get(pos+1).xCenterRight-lines.get(pos+1).xCenterLeft>0.3*(minMaxX[MAX]-minMaxX[MIN]))){
//                    cat = "\n";                    
//                }else{
//                    cat = " ";
//                }
                if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                    cat = "\n";
                }else{
                    cat = " ";
                }    
            }else if(textEndingInAPeriod.matcher(lines.get(pos).toString()).matches()){
//                if(lines.get(pos).xCenterRight-lines.get(pos).xCenterLeft<=0.75*(minMaxX[MAX]-minMaxX[MIN])
//                        || lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN],10)){
//                    cat = "\n";                    
//                }else{
//                    cat = " ";
//                }
                if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                    cat = "\n";
                }else{
                    cat = " ";
                }    
            }else if(textEndingInAComma.matcher(lines.get(pos).toString()).matches()&& textStartingInUppercase.matcher(lines.get(pos+1).toString()).matches()){
//                if(lines.get(pos).xCenterRight-lines.get(pos).xCenterLeft<=0.9*(minMaxX[MAX]-minMaxX[MIN])
//                        || lines.get(pos+1).xCenterLeft>Math.max(minMaxX[MIN],10)){
//                    cat = "\n";                    
//                }else{
//                    cat = " ";
//                }
                if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                    cat = "\n";
                }else{
                    cat = " ";
                }   
//            }else if(lines.get(pos+1).xCenterLeft - (lines.get(pos).heightSum/lines.size())*1.75 > minMaxX[MIN]){
//            }else if(lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN] * 1.4, 10)
//                        || ((lines.get(pos+1).xCenterLeft - lines.get(pos).heightSum/lines.size() > Math.max(minMaxX[MIN], 10))
//                        &&  lines.get(pos+1).xCenterRight-lines.get(pos+1).xCenterLeft<=0.3*(minMaxX[MAX]-minMaxX[MIN]))){ 
            }else if(isIndentedLine(lines, pos+1, minMaxX[MIN])){
                cat = "\n";
            }else{
                cat=" ";
            }
            strb.append(lines.get(pos).text.replaceFirst("-?\n?$", "").replaceAll("\n", " "));
            strb.append(cat);            
        }
        if(!lines.isEmpty()){
            strb.append(lines.get(lines.size()-1).text.replace("-?\n?$", "").replaceAll("\n", " "));
            strb.append("\n");
        }        
        return strb.toString();
    }
    
    private boolean isIndentedLine(List<Line> lines, int pos, int min){
        boolean ret;
        boolean before=true;
        boolean exit=true;
        int i;
        ret = lines.get(pos).xCenterLeft-min>Math.max(0.35*getAverageHeight(lines.get(pos)),10);
        if(ret){
            //per si el text està inclinat verticalment
            exit=false;
            for(i=pos-1;i>=0 && !exit;i--){
                if(lines.get(pos).xCenterLeft-lines.get(i).xCenterLeft<Math.min(-0.35*getAverageHeight(lines.get(pos)),-10)
                        && lines.get(i).xCenterLeft-min<Math.min(3*getAverageHeight(lines.get(pos)), 130)){
                    //la línia pos es troba significativament a l'esquerra de la linia pos
                    before = false;
                    exit = true;
                }else if(lines.get(pos).xCenterLeft-lines.get(i).xCenterLeft>Math.max(0.35*getAverageHeight(lines.get(pos)),10)){
                    exit = true;
                }
            }
        }
        if(ret && !(exit && before)){        
            //per si el text està inclinat verticalment
            exit=false;
            before=true;
            for(i=pos;i<lines.size() && !exit; i++){
                if(lines.get(pos).xCenterLeft-lines.get(i).xCenterLeft<Math.min(-0.35*getAverageHeight(lines.get(pos)),-10)
                        && lines.get(i).xCenterLeft-min<Math.min(3*getAverageHeight(lines.get(pos)), 130)){
                    //la línia pos es troba significativament a l'esquerra de la linia pos
                    ret = false;
                    exit = true;
                }else if(lines.get(pos).xCenterLeft-lines.get(i).xCenterLeft>Math.max(0.35*getAverageHeight(lines.get(pos)),10)){
                    exit = true;
                }
            }
        }
        return ret && before;
    }
    
    private int getAverageHeight(Line l){
        return l.heightSum/l.wordsOfLine.size();
    }
    
    public String getLines(){
       return getLines(-1);
    }
    
    public String getLines(int s){
        StringBuilder strb = new StringBuilder();
        LinePointer pointer = new LinePointer(documentResponse);
        if(s<=0){
            s=documentResponse.getPages(0).getLinesCount();
            for(int i=1; i< documentResponse.getPagesCount(); i++){
                s+=documentResponse.getPages(i).getLinesCount();
            }
        }
        int i=0;
        while (i<s && pointer.hasNext()){
            strb.append(pointer.getNext());
            i++;
        }
        return strb.toString();
    }
    
    public String getParagraphs(){
        return getParagraphs(-1);
    }
    
    public String getParagraphs(int s){
        StringBuilder strb = new StringBuilder();
        ParagraphPointer pointer = new ParagraphPointer(documentResponse);
        if(s<=0){
            s=documentResponse.getPages(0).getParagraphsCount();
            for(int i=1; i< documentResponse.getPagesCount(); i++){
                s+=documentResponse.getPages(i).getParagraphsCount();
            }
        }
        int i=0;
        while (i<s && pointer.hasNext()){
            strb.append(pointer.getNext());
            i++;
        }
        return strb.toString();
    }
    
    private static class ParagraphPointer extends Pointer{
        final static String EOL = "\n";
        boolean returnEol=false;
        int paragraf=0;

        public ParagraphPointer(Document doc) {
            super(doc);
        }
        
        public boolean hasNext(){
            if(paragraf>=document.getPages(page).getParagraphsCount()){
                if(returnEol){
                    paragraf=0;
                    page++;
                    returnEol=false;
                }else{
                    returnEol=true;
                }
            }
            return page<document.getPagesCount();
        }

        public String getNext(){
            String ret;
            String nextp;
            String cat="\n";
            if(returnEol){
                ret = EOL;
            }else{
                ret = getParagraph(page, paragraf, "\n");
                nextp = getParagraph(page, paragraf+1, "");
                int s = -1;
                Pattern p1 = Pattern.compile("^.*\\w\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p2 = Pattern.compile("^\\s*[^A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p3 = Pattern.compile("^.*[-¬]\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                if(p1.matcher(ret).matches() && p2.matcher(nextp).matches()){
                    cat = " ";
                }else if(p3.matcher(ret).matches()){
                    cat = "";
                    s=0;
                }
                ret = ret.replaceAll("[-¬]\\n", "").replaceAll(" ?\\n ?", " ");
                ret = ret.substring(0, ret.length()+s).concat(cat);
            }
            paragraf++;
            return ret;
        }
        
        private String getParagraph(int page, int paragraf, String def){
            String ret=def;
            if(paragraf>=document.getPages(page).getParagraphsCount()){
                if(page+1<document.getPagesCount()){
                    ret = getParagraph(page+1, 0, def);
                }
            }else{
                if (document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegmentsList().size() > 0) {
                    int startIdx = (int) document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegments(0).getStartIndex();
                    int endIdx = (int) document.getPages(page).getParagraphs(paragraf).getLayout().getTextAnchor().getTextSegments(0).getEndIndex();
                    ret = document.getText().substring(startIdx, endIdx);
                }
            }
            return ret;
        }
    }
    
    private static class LinePointer extends Pointer{
        final static String EOL = "\n";
        boolean returnEol=false;
        int line=0;

        public LinePointer(Document doc) {
            super(doc);
        }
        
        public boolean hasNext(){
            if(line>=document.getPages(page).getLinesCount()){
                if(returnEol){
                    line=0;
                    page++;
                    returnEol=false;
                }else{
                    returnEol=true;
                }
            }
            return page<document.getPagesCount();
        }

        public String getNext(){
            String ret;
            String nextp;
            String cat=" ";
            if(returnEol){
                ret = EOL;
            }else{
                ret = getLine(page, line, "\n");
                nextp = getLine(page, line+1, "");
                int s = -1;
                Pattern p11 = Pattern.compile("^.*\\w{2,}\\W\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p21 = Pattern.compile("^\\s*[A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p12 = Pattern.compile("^.*\\.\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p22 = Pattern.compile("^\\s*[0-9A-ZÁÀÄÂÉÈËÊÍÌÏÎÓÒÖÔÚÙÜÛÑ].*$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                Pattern p3 = Pattern.compile("^.*[-¬]\n$", Pattern.DOTALL+Pattern.UNICODE_CASE+Pattern.UNICODE_CHARACTER_CLASS);
                if(p11.matcher(ret).matches() && p21.matcher(nextp).matches()
                        || p12.matcher(ret).matches() && p22.matcher(nextp).matches()){
                    cat = "\n";
                }else if(p3.matcher(ret).matches()){
                    cat = "";
                    s=0;
                }
                ret = ret.replaceAll("[-¬]\\n", "").replaceAll(" ?\\n ?", " ");
                ret = ret.substring(0, ret.length()+s).concat(cat);
            }
            line++;
            return ret;
        }
        
        private String getLine(int page, int line, String def){
            String ret=def;
            if(line>=document.getPages(page).getLinesCount()){
                if(page+1<document.getPagesCount()){
                    ret = getLine(page+1, 0, def);
                }
            }else{
                if (document.getPages(page).getLines(line).getLayout().getTextAnchor().getTextSegmentsCount() > 0) {
                    int startIdx = (int) document.getPages(page).getLines(line).getLayout().getTextAnchor().getTextSegments(0).getStartIndex();
                    int endIdx = (int) document.getPages(page).getLines(line).getLayout().getTextAnchor().getTextSegments(0).getEndIndex();
                    ret = document.getText().substring(startIdx, endIdx);
                }
            }
            return ret;
        }
    }    
    
    private static final class Line extends Word{
        Deque<Word> wordsOfLine;
        SimpleRegression rLine = new SimpleRegression();
        Line bestLineForRegression;
        int distanceToBestRegression=0;

        public Line() {
            wordsOfLine = new LinkedList<>();            
            bestLineForRegression = this;
        }
        
        public Line(Word word){
            this();
            wordsOfLine.add(word);
            xCenterLeft = word.xCenterLeft;
            yCenterLeft = word.yCenterLeft;
            xCenterRight = word.xCenterRight;
            yCenterRight = word.yCenterRight;
            text = word.text;
            rLine.addData(xCenterLeft, yCenterLeft);
            rLine.addData(xCenterRight, yCenterRight);
            heightSum += word.heightSum;

        }
        
        public Line(Token token, Document document){
            this(new Word(token, document));
        }
        
        public boolean addWord(Line line, int thresholdx){
            boolean ret;
            if(wordsOfLine.isEmpty()){
                wordsOfLine.addAll(line.wordsOfLine);
                for(Word w: line.wordsOfLine){
                    rLine.addData(w.xCenterLeft, w.yCenterLeft);
                    rLine.addData(w.xCenterRight, w.yCenterRight);
                    heightSum += w.heightSum;
                }
                ret = true;
            }else{
                int thresholdy = (int) (0.52*Math.max(this.heightSum/(this.wordsOfLine.size()*2.0),line.heightSum/(line.wordsOfLine.size()*2.0)));
                int cmp = compareTo(line);
                if(cmp<0){
                    ret = this.areInTheSameLine(line, cmp, thresholdx, thresholdy);
                    if(ret){
                        while(!line.wordsOfLine.isEmpty()){
                            Word w = line.wordsOfLine.pollLast();
                            wordsOfLine.addFirst(w);
                            rLine.addData(w.xCenterLeft, w.yCenterLeft);
                            rLine.addData(w.xCenterRight, w.yCenterRight);
                            heightSum += w.heightSum;
                        }
                        xCenterLeft = line.xCenterLeft;
                        yCenterLeft = line.yCenterLeft;
                        text = line.text.concat(text);
                    }
                }else if(cmp>0){
                    ret = areInTheSameLine(line, cmp, thresholdx, thresholdy);
                    if(ret){
                        while(!line.wordsOfLine.isEmpty()){
                            Word w = line.wordsOfLine.pollFirst();
                            wordsOfLine.addLast(w);
                            rLine.addData(w.xCenterLeft, w.yCenterLeft);
                            rLine.addData(w.xCenterRight, w.yCenterRight);
                            heightSum += w.heightSum;
                        }
                        xCenterRight = line.xCenterRight;
                        yCenterRight = line.yCenterRight;
                        text = text.concat(line.text);
                    }
                }else{
                    //buscar si cabe donde cabe
                    ret = false;
                    for(int i=1; i<wordsOfLine.size(); i++){
                        if(((LinkedList<Word>)wordsOfLine).get(i-1).areInTheSameLine(line, cmp, thresholdx, thresholdy)
                                && ((LinkedList<Word>)wordsOfLine).get(i).areInTheSameLine(line, -1, thresholdx, thresholdy)){
                            int off=0;
                            for(Word w: line.wordsOfLine){
                                ((LinkedList<Word>)wordsOfLine).add(i+off, w);
                                rLine.addData(w.xCenterLeft, w.yCenterLeft);
                                rLine.addData(w.xCenterRight, w.yCenterRight);
                                heightSum += w.heightSum;
                                off++;
                            }
                            ret = true;
                            break;
                        }
                    }
                    if(ret){
                        StringBuilder strb = new StringBuilder();
                       for(Word w: wordsOfLine){
                            strb.append(w.text);
                        }
                        text = strb.toString();
                    }
                }
            }     
            return ret;
        }
        
        public void setBestLineForRegression(Line line){
            int xMid = (this.xCenterLeft+this.xCenterRight)/2;
            setBestLineForRegression(line, xMid);
        }
        
        public void setBestLineForRegression(Line line, int x){
            this.distanceToBestRegression = this.predictYFromRegression(x) - line.predictYFromRegression(x);
            this.bestLineForRegression = line.bestLineForRegression;
        }
        
        public int predictYFromRegression(int x){
            return (int) (this.bestLineForRegression.rLine.predict(x) + distanceToBestRegression);
        }
        
        @Override
        public int compareTo(Word word){
            int th;
            int ret;
            if(word instanceof Line){
                th = (int) (word.heightSum/(((Line)word).wordsOfLine.size()*2.0));
            }else{
                th = (int) (word.heightSum/2.0);
            }
            th = (int) (0.35*Math.max(this.heightSum/(this.wordsOfLine.size()*2.0),th));

            if(word.xCenterRight - th <= this.xCenterLeft){
                ret = -1;
            }else if(word.xCenterLeft + th >= this.xCenterRight){
                ret = 1;
            }else{
                //ERROR
                ret = 0;
            }
            return ret;
        }
        
        @Override
        public int getThresholdy(double distx){
            return (int) (Math.log(distx/getUsualDistance()+1)*this.heightSum/this.wordsOfLine.size());
        }
        
        @Override
        public double getUsualDistance(){
           return 0.6*this.heightSum/this.wordsOfLine.size(); 
        }
    }
    
    private static class Word{
        String text;
        int startIndex;
        int endIndex;
        int yCenterLeft;
        int yCenterRight;
        int xCenterLeft;
        int xCenterRight;
        int heightSum;
        
        protected Word(){
        }
        
        public Word(Token token, Document document){
            startIndex = (int) token.getLayout().getTextAnchor().getTextSegments(0).getStartIndex();
            endIndex = (int) token.getLayout().getTextAnchor().getTextSegments(0).getEndIndex();
            text = document.getText().substring(startIndex, endIndex);
            yCenterLeft= (token.getLayout().getBoundingPoly().getVertices(0).getY()+token.getLayout().getBoundingPoly().getVertices(3).getY())/2;
            yCenterRight= (token.getLayout().getBoundingPoly().getVertices(1).getY()+token.getLayout().getBoundingPoly().getVertices(2).getY())/2;
            xCenterLeft= (token.getLayout().getBoundingPoly().getVertices(0).getX()+token.getLayout().getBoundingPoly().getVertices(3).getX())/2;
            xCenterRight= (token.getLayout().getBoundingPoly().getVertices(1).getX()+token.getLayout().getBoundingPoly().getVertices(2).getX())/2;
            heightSum = token.getLayout().getBoundingPoly().getVertices(3).getY() - token.getLayout().getBoundingPoly().getVertices(0).getY() 
                            + token.getLayout().getBoundingPoly().getVertices(2).getY() - token.getLayout().getBoundingPoly().getVertices(1).getY();
        }
        
        protected boolean areInTheSameLine(Word word){
            boolean ret;
            int cmp = this.compareTo(word);
            if(cmp == 0){
                ret = false;
            }else{
                double distx = Math.min(Math.abs(word.xCenterRight - xCenterLeft), Math.abs(word.xCenterLeft - xCenterRight));
                int thresholdy = Math.max(this.getThresholdy(distx), word.getThresholdy(distx));
                ret =  areInTheSameLine(word, cmp, (int) (distx+100), thresholdy);
            }
            return ret;
        }
        
        protected boolean areInTheSameLine(Word word, int pos, int thresholdx, int thresholdy){
            boolean ret;
            if(pos<0){
                ret = Math.abs(word.yCenterRight - yCenterLeft) <= thresholdy && Math.abs(word.xCenterRight - xCenterLeft) <= thresholdx;
            }else{
                ret = Math.abs(word.yCenterLeft - yCenterRight) <= thresholdy && Math.abs(word.xCenterLeft - xCenterRight) <= thresholdx;
            }
            return ret;
        }

        @Override
        public String toString() {
            return text; 
        }
        
        public int getThresholdy(double distx){
            return (int) (Math.log(distx/getUsualDistance()+1)*this.heightSum);
        }
        
        public double getUsualDistance(){
           return 0.6*this.heightSum; 
        }
        
        public int compareTo(Word word){
            int th;
            int ret;
            th = (int) (0.35*word.heightSum/2.0);

            if(word.xCenterRight - th <= this.xCenterLeft){
                ret = -1;
            }else if(word.xCenterLeft + th >= this.xCenterRight){
                ret = 1;
            }else{
                //ERROR
                ret = 0;
            }
            return ret;
        }
        
    }
    
    private static class WordPointer extends Pointer{
        final static String EOL = "\n";
        boolean returnEol=false;
        int token=0;

        public WordPointer(Document doc) {
            super(doc);
        }
        
        public boolean hasNext(){
            if(token>=document.getPages(page).getTokensCount()){
                token=0;
                page++;
            }
            return page<document.getPagesCount();
        }

        public Word getNext(){
            Word ret;
                ret = getWord(page, token);
            token++;
            return ret;
        }
                
        private Word getWord(int page, int word){
            Word ret = null;
            if(word>=document.getPages(page).getTokensCount()){
                if(page+1<document.getPagesCount()){
                    ret = getWord(page+1, 0);
                }
            }else{
                ret = new Word(document.getPages(page).getTokens(word), document);
            }
            return ret;
        }
    }    

    private static class Pointer{
        Document document;
        int page =0;

        public Pointer(Document doc) {
            document = doc;
        }
    }

//      System.out.println("Document processing complete.");
//
//      // Read the text recognition output from the processor
//      // For a full list of Document object attributes,
//      // please reference this page:
//      // https://googleapis.dev/java/google-cloud-document-ai/latest/index.html
//
//      // Get all of the document text as one big string
//      String text = documentResponse.getText();
//      System.out.printf("Full document text: '%s'\n", escapeNewlines(text));
//
//      // Read the text recognition output from the processor
//      List<Document.Page> pages = documentResponse.getPagesList();
//      System.out.printf("There are %s page(s) in this document.\n", pages.size());
//
//      for (Document.Page page : pages) {
//        System.out.printf("Page %d:\n", page.getPageNumber());
//        printPageDimensions(page.getDimension());
//        printDetectedLanguages(page.getDetectedLanguagesList());
//        printParagraphs(page.getParagraphsList(), text);
//        printBlocks(page.getBlocksList(), text);
//        printLines(page.getLinesList(), text);
//        printTokens(page.getTokensList(), text);
//      }
//    }
//  }

  private static void printPageDimensions(Document.Page.Dimension dimension) {
    String unit = dimension.getUnit();
    System.out.printf("    Width: %.1f %s\n", dimension.getWidth(), unit);
    System.out.printf("    Height: %.1f %s\n", dimension.getHeight(), unit);
  }

  private static void printDetectedLanguages(
      List<Document.Page.DetectedLanguage> detectedLangauges) {
    System.out.println("    Detected languages:");
    for (Document.Page.DetectedLanguage detectedLanguage : detectedLangauges) {
      String languageCode = detectedLanguage.getLanguageCode();
      float confidence = detectedLanguage.getConfidence();
      System.out.printf("        %s (%.2f%%)\n", languageCode, confidence * 100.0);
    }
  }

  private static void printParagraphs(List<Document.Page.Paragraph> paragraphs, String text) {
    System.out.printf("    %d paragraphs detected:\n", paragraphs.size());
    Document.Page.Paragraph firstParagraph = paragraphs.get(0);
    String firstParagraphText = getLayoutText(firstParagraph.getLayout().getTextAnchor(), text);
    System.out.printf("        First paragraph text: %s\n", escapeNewlines(firstParagraphText));
    Document.Page.Paragraph lastParagraph = paragraphs.get(paragraphs.size() - 1);
    String lastParagraphText = getLayoutText(lastParagraph.getLayout().getTextAnchor(), text);
    System.out.printf("        Last paragraph text: %s\n", escapeNewlines(lastParagraphText));
  }

  private static void printBlocks(List<Document.Page.Block> blocks, String text) {
    System.out.printf("    %d blocks detected:\n", blocks.size());
    Document.Page.Block firstBlock = blocks.get(0);
    String firstBlockText = getLayoutText(firstBlock.getLayout().getTextAnchor(), text);
    System.out.printf("        First block text: %s\n", escapeNewlines(firstBlockText));
    Document.Page.Block lastBlock = blocks.get(blocks.size() - 1);
    String lastBlockText = getLayoutText(lastBlock.getLayout().getTextAnchor(), text);
    System.out.printf("        Last block text: %s\n", escapeNewlines(lastBlockText));
  }

  private static void printLines(List<Document.Page.Line> lines, String text) {
    System.out.printf("    %d lines detected:\n", lines.size());
    Document.Page.Line firstLine = lines.get(0);
    String firstLineText = getLayoutText(firstLine.getLayout().getTextAnchor(), text);
    System.out.printf("        First line text: %s\n", escapeNewlines(firstLineText));
    Document.Page.Line lastLine = lines.get(lines.size() - 1);
    String lastLineText = getLayoutText(lastLine.getLayout().getTextAnchor(), text);
    System.out.printf("        Last line text: %s\n", escapeNewlines(lastLineText));
  }

  private static void printTokens(List<Document.Page.Token> tokens, String text) {
    System.out.printf("    %d tokens detected:\n", tokens.size());
    Document.Page.Token firstToken = tokens.get(0);
    String firstTokenText = getLayoutText(firstToken.getLayout().getTextAnchor(), text);
    System.out.printf("        First token text: %s\n", escapeNewlines(firstTokenText));
    Document.Page.Token lastToken = tokens.get(tokens.size() - 1);
    String lastTokenText = getLayoutText(lastToken.getLayout().getTextAnchor(), text);
    System.out.printf("        Last token text: %s\n", escapeNewlines(lastTokenText));
  }

  // Extract shards from the text field
  private static String getLayoutText(Document.TextAnchor textAnchor, String text) {
    if (textAnchor.getTextSegmentsList().size() > 0) {
      int startIdx = (int) textAnchor.getTextSegments(0).getStartIndex();
      int endIdx = (int) textAnchor.getTextSegments(0).getEndIndex();
      return text.substring(startIdx, endIdx);
    }
    return "[NO TEXT]";
  }

  private static String escapeNewlines(String s) {
    return s.replaceAll("\n", "\\n").replaceAll("\r", "\\r");
  }
}